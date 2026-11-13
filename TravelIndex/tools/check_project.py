#!/usr/bin/env python3
"""Comprobaciones estáticas rápidas. NO sustituyen a compilar con Gradle."""
import pathlib, re, sys, xml.dom.minidom
root = pathlib.Path(__file__).resolve().parent.parent
FORBIDDEN = [w for w in sys.argv[1:]]   # nombres antiguos a detectar: tools/check_project.py NombreViejo
bad = 0
for p in root.rglob("*"):
    if not p.is_file() or any(s in p.parts for s in (".git", "build", ".gradle")) or p.name == "check_project.py": continue
    if p.suffix in {".kt", ".kts", ".xml", ".toml", ".md", ".properties", ".pro"}:
        t = p.read_text(encoding="utf-8")
        if p.suffix in {".kt", ".kts"}:
            code = re.sub(r"(?s:/\*.*?\*/)|//[^\n]*|\"\"\"(?s:.*?)\"\"\"|\"(?:\\.|[^\"\\\n])*\"|'(?:\\.|[^'\\\n])'", "", t)
            for a, b in ("{}", "()", "[]"):
                if code.count(a) != code.count(b): print(f"[BALANCE {a}{b}] {p.relative_to(root)}"); bad += 1
        if p.suffix == ".xml":
            try: xml.dom.minidom.parseString(t.encode())
            except Exception as e: print(f"[XML] {p.relative_to(root)}: {e}"); bad += 1
        for w in FORBIDDEN:
            if w.lower() in t.lower(): print(f"[NOMBRE ANTIGUO '{w}'] {p.relative_to(root)}"); bad += 1
        for m in re.finditer(r"\b[Tt]ravel[_-][Ii]ndex\b|\bTRAVELINDEX\b|\bTravelindex\b|\bTravel index\b|\btravel Index\b", t):
            print(f"[GRAFÍA] '{m.group(0)}' en {p.relative_to(root)}"); bad += 1
print("Sin incidencias" if not bad else f"{bad} incidencia(s)"); sys.exit(1 if bad else 0)
