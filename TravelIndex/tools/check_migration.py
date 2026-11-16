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
expected = entities()

def table_ddl(name, cols, skip=()):
    parts = []
    for c, (aff, nn, pk) in cols.items():
        if c in skip: continue
        parts.append("id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL" if pk else f"{c} {aff}" + (" NOT NULL" if nn else ""))
    return f"CREATE TABLE {name} ({', '.join(parts)}, FOREIGN KEY(tripId) REFERENCES trips(id) ON UPDATE NO ACTION ON DELETE CASCADE)"

con = sqlite3.connect(":memory:")
con.execute("CREATE TABLE trips (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
# La tabla de actividades existe desde la versión 1 (no la crea ninguna migración): se parte de su forma en la versión 3.
con.execute(table_ddl("activities", expected["activities"], skip=("durationMinutes",)))
con.execute("CREATE INDEX index_activities_tripId ON activities (tripId)")
for s in sql: con.execute(s)
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
# --- v2 -> v3: columna travelModes del viaje ---
trips_exp = expected.get("trips", {})
if trips_exp.get("travelModes") != ("TEXT", 1, 0):
    print(f"[X] trips.travelModes en la entidad: {trips_exp.get('travelModes')}"); bad += 1
trips_got = {r[1]: (r[2].upper(), r[3], r[5], r[4]) for r in con.execute("PRAGMA table_info(trips)")}
tm = trips_got.get("travelModes")
if tm is None or tm[0] != "TEXT" or tm[1] != 1 or tm[2] != 0:
    print(f"[X] trips.travelModes tras la migración: {tm}"); bad += 1

# --- v3 -> v4: columna durationMinutes de la actividad ---
act_exp = expected.get("activities", {})
if act_exp.get("durationMinutes") != ("INTEGER", 0, 0):
    print(f"[X] activities.durationMinutes en la entidad: {act_exp.get('durationMinutes')}"); bad += 1
act_got = {r[1]: (r[2].upper(), r[3], 1 if r[5] > 0 else 0) for r in con.execute("PRAGMA table_info(activities)")}
if act_got != act_exp:
    print(f"[X] activities tras la migración: sobran {set(act_got)-set(act_exp)}, faltan {set(act_exp)-set(act_got)}, distintas "
          f"{[k for k in act_exp if k in act_got and act_got[k] != act_exp[k]]}"); bad += 1
# Simulación real: una actividad de la versión 3 (con hora de fin y sin duración) debe sobrevivir intacta.
sim3 = sqlite3.connect(":memory:")
sim3.execute("CREATE TABLE trips (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
sim3.execute(table_ddl("activities", act_exp, skip=("durationMinutes",)))
v3_cols = [c for c in act_exp if c not in ("durationMinutes", "id")]
v3_vals = ", ".join("'x'" if act_exp[c][0] == "TEXT" else "1" for c in v3_cols)
sim3.execute(f"INSERT INTO activities ({', '.join(v3_cols)}) VALUES ({v3_vals})")
m34 = re.search(r"MIGRATION_3_4.*?db\.execSQL\((.*?)\)\n", mig, flags=re.S)
if not m34: print("[X] MIGRATION_3_4 no encontrada"); bad += 1
else:
    sim3.execute("".join(re.findall(r'"((?:\\.|[^"\\])*)"', m34.group(1))))
    row = sim3.execute("SELECT name, endTime, durationMinutes FROM activities").fetchone()
    if row != ("x", 1, None): print(f"[X] la actividad existente quedó como {row}"); bad += 1

# Simulación real: un viaje de la versión 2 (sin la columna) debe sobrevivir y quedar con valor vacío.
sim = sqlite3.connect(":memory:")
v2_cols = [c for c in trips_exp if c != "travelModes"]
ddl = ", ".join(("id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL" if c == "id" else f"{c} {trips_exp[c][0]} NOT NULL" if trips_exp[c][1] else f"{c} {trips_exp[c][0]}") for c in v2_cols)
sim.execute(f"CREATE TABLE trips ({ddl})")
vals = ", ".join("1" if trips_exp[c][0] == "INTEGER" else "'x'" for c in v2_cols)
sim.execute(f"INSERT INTO trips ({', '.join(v2_cols)}) VALUES ({vals})")
m23 = re.search(r"MIGRATION_2_3.*?db\.execSQL\((.*?)\)\n", mig, flags=re.S)
if not m23: print("[X] MIGRATION_2_3 no encontrada"); bad += 1
else:
    stmt = "".join(re.findall(r'"((?:\\.|[^"\\])*)"', m23.group(1)))
    sim.execute(stmt)
    row = sim.execute("SELECT travelModes FROM trips").fetchone()
    if row != ("",): print(f"[X] el viaje existente quedó con travelModes={row}"); bad += 1

# La versión de la base de datos y las migraciones registradas deben encajar.
dbsrc = (root / "app/src/main/java/com/travelindex/app/data/db/TravelIndexDatabase.kt").read_text(encoding="utf-8")
module = (root / "app/src/main/java/com/travelindex/app/di/DatabaseModule.kt").read_text(encoding="utf-8")
ver = int(re.search(r"const val VERSION = (\d+)", dbsrc).group(1))
ends = sorted(int(m.group(2)) for m in re.finditer(r"Migration\((\d+), (\d+)\)", mig))
if ends != list(range(2, ver + 1)): print(f"[X] migraciones {ends} no cubren hasta la versión {ver}"); bad += 1
reg = re.search(r"addMigrations\((.*?)\)", module)
names = [n.strip() for n in reg.group(1).split(",")] if reg else []
want = [f"MIGRATION_{i-1}_{i}" for i in range(2, ver + 1)]
if names != want: print(f"[X] DatabaseModule registra {names}, se esperaba {want}"); bad += 1
print("Migración coherente con las entidades" if not bad else f"{bad} incoherencia(s)")
sys.exit(1 if bad else 0)
