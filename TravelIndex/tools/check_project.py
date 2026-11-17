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
# Catalogo de versiones: Gradle aborta antes de empezar si el TOML esta mal o repite una clave
# (tomllib tambien rechaza claves duplicadas) o si un build.gradle.kts usa un libs.* que no existe.
try:
    import tomllib
except ImportError:
    tomllib = None
cat_path = root / "gradle" / "libs.versions.toml"
if tomllib and cat_path.exists():
    try:
        cat = tomllib.loads(cat_path.read_text(encoding="utf-8"))
    except Exception as e:
        print(f"[CATALOGO] libs.versions.toml: {e}"); bad += 1
        cat = None
    if cat:
        known = {"libs." + k.replace("-", ".") for k in cat.get("libraries", {})}
        known |= {"libs.plugins." + k.replace("-", ".") for k in cat.get("plugins", {})}
        known |= {"libs.versions." + k.replace("-", ".") for k in cat.get("versions", {})}
        groups = {".".join(k.split(".")[:i]) for k in known for i in range(2, len(k.split(".")))}
        for g in list(root.rglob("build.gradle.kts")) + list(root.rglob("settings.gradle.kts")):
            if any(s in g.parts for s in ("build", ".gradle")): continue
            for m in re.finditer(r"\blibs(?:\.[A-Za-z][A-Za-z0-9_]*)+", g.read_text(encoding="utf-8")):
                ref = re.sub(r"\.(get|asProvider|asInt|asString)$", "", m.group(0))
                if ref not in known and ref not in groups:
                    print(f"[CATALOGO] {g.relative_to(root)}: '{ref}' no esta en libs.versions.toml"); bad += 1
print("Sin incidencias" if not bad else f"{bad} incidencia(s)"); sys.exit(1 if bad else 0)
