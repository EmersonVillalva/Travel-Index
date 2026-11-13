#!/usr/bin/env python3
"""Compara el SQL de Migrations.kt con las entidades Room de Entities.kt (columnas, tipos, NOT NULL, PK, FK, índices).
Replica las reglas de validación de Room (afinidad de tipo, nulabilidad, clave primaria, claves foráneas).
Uso: python3 tools/check_migration.py"""
import pathlib, re, sqlite3, sys
root = pathlib.Path(__file__).resolve().parent.parent
ent = (root / "app/src/main/java/com/travelindex/app/data/db/Entities.kt").read_text(encoding="utf-8")
mig = (root / "app/src/main/java/com/travelindex/app/data/db/Migrations.kt").read_text(encoding="utf-8")

AFF = {"Long": "INTEGER", "Int": "INTEGER", "Boolean": "INTEGER", "LocalDate": "INTEGER", "LocalTime": "INTEGER",
       "String": "TEXT", "Double": "REAL", "Float": "REAL"}

def entities():
    out = {}
    for m in re.finditer(r"@Entity\((.*?)\)\s*\ndata class (\w+)\((.*?)\n\)", ent, flags=re.S):
        head, cls, body = m.groups()
        t = re.search(r'tableName = "(\w+)"', head)
        if not t: continue
        cols = {}
        for line in body.splitlines():
            line = line.strip()
            if not line or line.startswith("//") or line.startswith("/**") or line.startswith("*"): continue
            f = re.match(r"(?:@PrimaryKey\(autoGenerate = true\)\s*)?val (\w+): (\w+)(\?)?", line)
            if f:
                n, ty, nullable = f.groups()
                cols[n] = (AFF[ty], 0 if (nullable or "PrimaryKey" in line) and not "PrimaryKey" in line else 1, 1 if "PrimaryKey" in line else 0)
                if nullable: cols[n] = (AFF[ty], 0, 0)
        out[t.group(1)] = cols
    return out

sql = []
for call in re.finditer(r"db\.execSQL\((.*?)\n?\s*\)\n", mig, flags=re.S):
    lits = re.findall(r'"((?:\\.|[^"\\])*)"', call.group(1))
    sql.append("".join(lits).replace("\\\"", '"'))
con = sqlite3.connect(":memory:")
con.execute("CREATE TABLE trips (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
for s in sql: con.execute(s)

expected = entities()
bad = 0
for table in ["transports", "restaurants", "travel_docs", "note_items"]:
    exp = expected.get(table)
    if exp is None: print(f"[X] entidad {table} no encontrada"); bad += 1; continue
    got = {r[1]: (r[2].upper(), r[3], r[5]) for r in con.execute(f"PRAGMA table_info({table})")}
    if set(got) != set(exp): print(f"[X] {table}: columnas distintas. Solo en SQL: {set(got)-set(exp)}; solo en entidad: {set(exp)-set(got)}"); bad += 1
    for c, (a, nn, pk) in exp.items():
        if c in got:
            g = got[c]
            if g[0] != a or g[1] != nn or (g[2] > 0) != bool(pk):
                print(f"[X] {table}.{c}: SQL={g} entidad={(a, nn, pk)}"); bad += 1
    fk = list(con.execute(f"PRAGMA foreign_key_list({table})"))
    if not (len(fk) == 1 and fk[0][2] == "trips" and fk[0][3] == "tripId" and fk[0][4] == "id" and fk[0][6] == "CASCADE" and fk[0][5] == "NO ACTION"):
        print(f"[X] {table}: clave foránea incorrecta {fk}"); bad += 1
    idx = [r[1] for r in con.execute(f"PRAGMA index_list({table})")]
    if idx != [f"index_{table}_tripId"]: print(f"[X] {table}: índices {idx}"); bad += 1
print("Migración coherente con las entidades" if not bad else f"{bad} incoherencia(s)")
sys.exit(1 if bad else 0)
