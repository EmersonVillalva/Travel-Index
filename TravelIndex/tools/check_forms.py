#!/usr/bin/env python3
"""Comprueba que cada campo de los formularios (ModuleSpecs / TripSpec) exista en su Codec:
   - la clave se rellena en `values(...)` (para editar) y
   - la clave se lee en `build(...)`/validación (para guardar).
Un campo que solo existe en un lado se guardaría vacío o no se mostraría al editar."""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "app/src/main/java/com/travelindex/app"
specs = (ROOT / "ui/modules/ModuleSpecs.kt").read_text(encoding="utf-8")
codecs = (ROOT / "domain/forms/Codecs.kt").read_text(encoding="utf-8")
trip_spec = (ROOT / "ui/trips/TripSpec.kt").read_text(encoding="utf-8")
trip_codec = (ROOT / "domain/forms/TripCodec.kt").read_text(encoding="utf-8")

MAP = {"flight": "FlightCodec", "lodging": "LodgingCodec", "activity": "ActivityCodec", "transport": "TransportCodec",
       "restaurant": "RestaurantCodec", "travelDoc": "TravelDocCodec", "expense": "ExpenseCodec", "destination": "DestinationCodec"}
problems = []


def object_text(src, name):
    m = re.search(r"^object\s+" + name + r"\b", src, re.M)
    if not m:
        return None
    nxt = re.search(r"^(?:object|class|internal|private|/\*\*|data class)\b", src[m.end():], re.M)
    return src[m.start(): m.end() + (nxt.start() if nxt else len(src))]


def keys_set(body):
    values = body.split("fun build", 1)[0]
    vkeys = set(re.findall(r'"(\w+)"\s+to\b', values))
    build = body.split("fun build", 1)[1] if "fun build" in body else ""
    rkeys = set(re.findall(r'\bv\.\w+\(\s*"(\w+)"', body)) | set(re.findall(r'\bv\["(\w+)"\]', body))
    rkeys |= set(re.findall(r'\.(?:str|int|date|time|minor|money|choice|zone|currency)\(\s*"(\w+)"', build))
    return vkeys, rkeys, body


# ---- Módulos ---------------------------------------------------------------------------------------------------
for spec_name, codec_name in MAP.items():
    m = re.search(r"private val " + spec_name + r"\s*=\s*ModuleSpec\(", specs)
    if not m:
        problems.append(f"No se encuentra la spec '{spec_name}'")
        continue
    rest = specs[m.end():]
    nxt = re.search(r"\n    private val \w+\s*=\s*ModuleSpec\(|\n    fun of\(|\n    fun spec\(", rest)
    block = rest[: nxt.start()] if nxt else rest
    skeys = re.findall(r'\bf\(\s*"(\w+)"', block)
    body = object_text(codecs, codec_name)
    if body is None:
        problems.append(f"No se encuentra {codec_name}")
        continue
    vkeys, rkeys, _ = keys_set(body)
    for k in skeys:
        if k not in vkeys:
            problems.append(f"{spec_name}: el campo '{k}' no se rellena en {codec_name}.values() (al editar saldría vacío)")
        if k not in rkeys and k not in ("currency",):
            problems.append(f"{spec_name}: el campo '{k}' no se lee en {codec_name}.build()/cross() (no se guardaría)")
    dup = {k for k in skeys if skeys.count(k) > 1}
    for k in dup:
        problems.append(f"{spec_name}: campo '{k}' repetido")
    print(f"  {spec_name:11s} {len(skeys):2d} campos ↔ {codec_name}")

# ---- Viaje -----------------------------------------------------------------------------------------------------
tkeys = re.findall(r'FieldDef\(\s*"(\w+)"', trip_spec)
tv = set(re.findall(r'"(\w+)"\s+to\b', trip_codec))
tr = set(re.findall(r'\bv\.\w+\(\s*"(\w+)"', trip_codec)) | set(re.findall(r'\bv\["(\w+)"\]', trip_codec))
for k in tkeys:
    if k not in tv:
        problems.append(f"trip: '{k}' no se rellena en TripCodec.values()")
    if k not in tr and k not in ("currency",):
        problems.append(f"trip: '{k}' no se lee en TripCodec")
print(f"  trip        {len(tkeys):2d} campos ↔ TripCodec")

if problems:
    print("\nPROBLEMAS:")
    for p in problems:
        print(" -", p)
    sys.exit(1)
print("OK: formularios coherentes con sus códecs.")
