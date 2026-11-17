#!/usr/bin/env python3
"""Comprueba que los recursos usados en el código existen y que los textos con argumentos coinciden.

Uso:  python3 tools/check_resources.py
Revisa:
  * R.string.X en Kotlin  -> debe existir <string name="X"> en res/values/strings.xml
  * @string/X en XML       -> idem
  * R.drawable / R.mipmap / @drawable / @mipmap / @xml -> deben existir
  * número de argumentos de stringResource/getString frente a los marcadores %1$s, %2$d...
  * marcadores no posicionales con más de uno (error de aapt2) y apóstrofos sin escapar
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MAIN = ROOT / "app" / "src" / "main"
RES = MAIN / "res"

errors = []
warnings = []


def read(p):
    return p.read_text(encoding="utf-8")


# ---- Textos definidos -------------------------------------------------------------------------------
strings = {}
for f in sorted((RES).glob("values*/strings.xml")):
    txt = read(f)
    for m in re.finditer(r'<string\s+name="([^"]+)"([^>]*)>(.*?)</string>', txt, re.S):
        name, attrs, body = m.group(1), m.group(2), m.group(3)
        if f.parent.name == "values":
            if name in strings:
                errors.append(f"texto duplicado: {name}")
            strings[name] = (body, 'formatted="false"' in attrs)
        # apóstrofos sin escapar (aapt2 los rechaza salvo dentro de comillas)
        if re.search(r"(?<!\\)'", body) and not (body.strip().startswith('"') and body.strip().endswith('"')):
            errors.append(f"{f.name}: apóstrofo sin escapar en '{name}'")
        if "&" in body and not re.search(r"&(amp|lt|gt|quot|apos|#\d+);", body):
            errors.append(f"{f.name}: '&' sin escapar en '{name}'")
        if "<" in body and "<b>" not in body and "<i>" not in body and "<u>" not in body:
            errors.append(f"{f.name}: '<' sin escapar en '{name}'")
        positional = re.findall(r"%(\d+)\$", body)
        plain = re.findall(r"%(?!\d+\$)(?:[-#+ 0,(]*\d*(?:\.\d+)?)[sdfxXcb]", body)
        if len(plain) > 1 and 'formatted="false"' not in attrs:
            errors.append(f"'{name}': varios marcadores no posicionales (usa %1$s, %2$s)")
        if plain and positional:
            errors.append(f"'{name}': mezcla marcadores posicionales y no posicionales")

plurals = set()
for f in sorted(RES.glob("values/*.xml")):
    for m in re.finditer(r'<plurals\s+name="([^"]+)"', read(f)):
        plurals.add(m.group(1))

# ---- Referencias en Kotlin ----------------------------------------------------------------------------
kt_files = sorted((MAIN / "java").rglob("*.kt"))
used_strings = {}
call_args = []


def count_top_level_args(src, start):
    """src[start] es el primer carácter tras 'R.string.X'. Devuelve nº de argumentos adicionales o None."""
    i = start
    while i < len(src) and src[i] in " \t\n":
        i += 1
    if i >= len(src) or src[i] != ",":
        return 0
    depth = 0
    count = 0
    j = i
    in_str = False
    prev = ""
    while j < len(src):
        c = src[j]
        if in_str:
            if c == '"' and prev != "\\":
                in_str = False
        else:
            if c == '"':
                in_str = True
            elif c in "([{":
                depth += 1
            elif c in ")]}":
                if depth == 0:
                    # fin de la llamada
                    return count if count else 0
                depth -= 1
            elif c == "," and depth == 0:
                count += 1
        prev = c
        j += 1
    return None


for f in kt_files:
    src = read(f)
    for m in re.finditer(r"R\.string\.([A-Za-z0-9_]+)", src):
        name = m.group(1)
        used_strings.setdefault(name, []).append(f.name)
        # Solo se cuentan argumentos cuando el texto es el primer parámetro de stringResource(...) / getString(...)
        if re.search(r"(stringResource|getString)\(\s*$", src[max(0, m.start() - 40):m.start()]):
            n = count_top_level_args(src, m.end())
            if n is not None:
                call_args.append((name, n, f.name))

for name, files in sorted(used_strings.items()):
    if name not in strings:
        errors.append(f"R.string.{name} no existe (usado en {sorted(set(files))[0]})")

for f in sorted(RES.rglob("*.xml")) + [MAIN / "AndroidManifest.xml"]:
    for m in re.finditer(r"@string/([A-Za-z0-9_]+)", read(f)):
        used_strings.setdefault(m.group(1), []).append(f.name)
        if m.group(1) not in strings:
            errors.append(f"@string/{m.group(1)} no existe (usado en {f.name})")

for name, n, fname in call_args:
    if name not in strings:
        continue
    body, nofmt = strings[name]
    pos = [int(x) for x in re.findall(r"%(\d+)\$", body)]
    plain = re.findall(r"%(?!\d+\$)(?:[-#+ 0,(]*\d*(?:\.\d+)?)[sdfxXcb]", body)
    expected = max(pos) if pos else len(plain)
    if nofmt:
        expected = 0
    if n != expected:
        errors.append(f"'{name}' ({fname}): se pasan {n} argumentos y el texto espera {expected}")

# ---- Otros recursos -------------------------------------------------------------------------------------
def res_names(folder_prefix):
    out = set()
    for d in RES.glob(folder_prefix + "*"):
        for f in d.iterdir():
            out.add(f.stem)
    return out

drawables = res_names("drawable")
mipmaps = res_names("mipmap")
xmls = res_names("xml")
for f in kt_files:
    src = read(f)
    for kind, have in (("drawable", drawables), ("mipmap", mipmaps), ("xml", xmls)):
        for m in re.finditer(r"R\.%s\.([A-Za-z0-9_]+)" % kind, src):
            if m.group(1) not in have:
                errors.append(f"R.{kind}.{m.group(1)} no existe (usado en {f.name})")
for f in sorted(RES.rglob("*.xml")) + [MAIN / "AndroidManifest.xml"]:
    src = read(f)
    for kind, have in (("drawable", drawables), ("mipmap", mipmaps), ("xml", xmls)):
        for m in re.finditer(r"@%s/([A-Za-z0-9_]+)" % kind, src):
            if m.group(1) not in have:
                errors.append(f"@{kind}/{m.group(1)} no existe (usado en {f.name})")

for f in kt_files:
    for m in re.finditer(r"R\.plurals\.([A-Za-z0-9_]+)", read(f)):
        if m.group(1) not in plurals:
            errors.append(f"R.plurals.{m.group(1)} no existe (usado en {f.name})")

unused = sorted(n for n in strings if n not in used_strings and n not in ("app_name",))
if unused:
    warnings.append("textos sin usar (no es un error): " + ", ".join(unused))

# ---- Paridad entre idiomas ---------------------------------------------------------------------------
# El archivo base (values/) es el español (idioma por defecto) y values-en/ el inglés: deben tener exactamente las
# mismas claves y los mismos marcadores %n$x en cada texto, o un idioma se caería al otro (o rompería el formato).
def parse_locale(path):
    txt = read(path)
    sts = {m.group(1): m.group(2) for m in re.finditer(r'<string name="([^"]+)"[^>]*>(.*?)</string>', txt, re.S)}
    pls = {m.group(1): m.group(2) for m in re.finditer(r'<plurals name="([^"]+)">(.*?)</plurals>', txt, re.S)}
    return sts, pls

def placeholders(s):
    return sorted(re.findall(r"%\d+\$[sdf]", s))

base_s, base_p = parse_locale(RES / "values" / "strings.xml")
for loc_dir in sorted(RES.glob("values-*")):
    f = loc_dir / "strings.xml"
    if not f.exists():
        continue
    loc = loc_dir.name
    loc_s, loc_p = parse_locale(f)
    # La marca (translatable=false) vive solo en el base.
    base_keys = {k for k in base_s if k not in ("app_name", "app_tagline")}
    missing = sorted(base_keys - set(loc_s))
    extra = sorted(set(loc_s) - set(base_s))
    if missing:
        errors.append(f"{loc}: faltan textos: {', '.join(missing[:8])}{'…' if len(missing) > 8 else ''}")
    if extra:
        errors.append(f"{loc}: textos que no existen en el base: {', '.join(extra[:8])}")
    if set(base_p) != set(loc_p):
        errors.append(f"{loc}: plurals distintos: {sorted(set(base_p) ^ set(loc_p))}")
    for k in base_keys & set(loc_s):
        if placeholders(base_s[k]) != placeholders(loc_s[k]):
            errors.append(f"{loc}: marcadores distintos en {k}")

for w in warnings:
    print("AVISO:", w)
if errors:
    print(f"\n{len(errors)} problema(s):")
    for e in errors:
        print(" -", e)
    sys.exit(1)
print(f"OK: {len(strings)} textos definidos, {len(used_strings)} referenciados, sin problemas.")
