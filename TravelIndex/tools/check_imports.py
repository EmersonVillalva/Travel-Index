#!/usr/bin/env python3
"""Revisión estática de imports de Kotlin (no sustituye a compilar, pero caza los olvidos típicos).

1) Símbolos del propio proyecto usados sin import (clases, objetos y funciones de otros paquetes).
2) Símbolos de librerías que ESTE proyecto importa en algún archivo y que otro archivo usa sin importarlos.
3) Imports que apuntan a símbolos del proyecto que no existen.
4) Delegados `by` sin los imports getValue/setValue.
"""
import re
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FILES = sorted((ROOT / "app" / "src").rglob("*.kt"))
BASE = "com.travelindex.app"


def strip(src):
    """Quita comentarios y contenido de cadenas (conserva saltos de línea)."""
    out = []
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if src.startswith("//", i):
            j = src.find("\n", i)
            i = n if j < 0 else j
        elif src.startswith("/*", i):
            j = src.find("*/", i + 2)
            j = n if j < 0 else j + 2
            out.append("\n" * src.count("\n", i, j))
            i = j
        elif src.startswith('"""', i):
            j = src.find('"""', i + 3)
            j = n if j < 0 else j + 3
            out.append('""' + "\n" * src.count("\n", i, j))
            i = j
        elif c == '"':
            j = i + 1
            while j < n and src[j] != '"':
                if src[j] == "\\":
                    j += 1
                j += 1
            out.append('""')
            i = j + 1
        elif c == "'" and i + 2 < n and (src[i + 2] == "'" or (src[i + 1] == "\\" and src[i + 3] == "'")):
            j = src.find("'", i + 2)
            out.append("' '")
            i = j + 1
        else:
            out.append(c)
            i += 1
    return "".join(out)


class F:
    pass


files = []
for p in FILES:
    f = F()
    f.path = p
    f.rel = str(p.relative_to(ROOT))
    f.src = strip(p.read_text(encoding="utf-8"))
    m = re.search(r"^package\s+([\w.]+)", f.src, re.M)
    f.pkg = m.group(1) if m else ""
    f.imports = {}
    f.star = set()
    for im in re.finditer(r"^import\s+([\w.]+)(?:\.\*|\s+as\s+(\w+))?", f.src, re.M):
        full = im.group(0)[7:].strip()
        if full.endswith(".*"):
            f.star.add(full[:-2])
        else:
            name = im.group(2) or full.split(" as ")[0].split(".")[-1]
            f.imports[name] = full.split(" as ")[0]
    files.append(f)

# ---- Declaraciones públicas de primer nivel del proyecto ----------------------------------------------------
top_types = defaultdict(set)    # nombre -> {paquete}
top_funs = defaultdict(set)     # nombre -> {paquete}  (funciones/valores de primer nivel sin receptor)
top_ext = defaultdict(set)      # nombre -> {paquete}  (extensiones)
all_decl_names = defaultdict(set)  # nombre -> archivos que lo declaran (cualquier nivel)
for f in files:
    for m in re.finditer(r"^(?!\s*private\b)(?:(?:internal|public|data|sealed|enum|abstract|open|annotation|value|inline)\s+)*"
                         r"(?:class|object|interface|typealias)\s+(\w+)", f.src, re.M):
        top_types[m.group(1)].add(f.pkg)
    for m in re.finditer(r"^(?!\s*private\b)((?:(?:internal|public|inline|suspend|operator|tailrec)\s+)*)fun\s+(?:<[^>]*>\s*)?(\w+(?:<[^>]*>)?\.)?(\w+)\s*\(", f.src, re.M):
        if m.group(2):
            # las extensiones `internal` sobre alias (p. ej. FormValues.date) chocan con miembros homónimos: se omiten
            if "internal" not in m.group(1):
                top_ext[m.group(3)].add(f.pkg)
        else:
            top_funs[m.group(3)].add(f.pkg)
    for m in re.finditer(r"^(?!\s*private\b)(?:(?:internal|public|const)\s+)*(?:val|var)\s+(\w+)\s*[:=]", f.src, re.M):
        top_funs[m.group(1)].add(f.pkg)
    for m in re.finditer(r"\b(?:class|object|interface|fun|val|var|typealias)\s+(?:<[^>]*>\s*)?(?:[\w<>?, ]+\.)?(\w+)", f.src):
        all_decl_names[m.group(1)].add(f.rel)
    # parámetros de funciones y lambdas
    for m in re.finditer(r"[(,]\s*(?:val\s+|var\s+)?(\w+)\s*:", f.src):
        all_decl_names[m.group(1)].add(f.rel)

problems = []

# ---- 3) imports del proyecto que no existen ---------------------------------------------------------------------
for f in files:
    for name, full in f.imports.items():
        if full.startswith(BASE + "."):
            pkg = full.rsplit(".", 1)[0]
            sym = full.rsplit(".", 1)[1]
            ok = pkg in top_types.get(sym, set()) or pkg in top_funs.get(sym, set()) or pkg in top_ext.get(sym, set())
            # miembros de objetos / enums:  com.x.Obj.member
            if not ok:
                parent = pkg.rsplit(".", 1)
                if len(parent) == 2 and parent[0] in top_types.get(parent[1], set()):
                    ok = True
            if not ok and sym in ("R", "BuildConfig"):
                ok = True   # los genera Android Gradle Plugin
            if not ok:
                problems.append(f"{f.rel}: import inexistente {full}")

# ---- 1) símbolos del proyecto sin import --------------------------------------------------------------------------
def visible(f, pkg_of_symbol, name):
    if pkg_of_symbol == f.pkg:
        return True
    if name in f.imports:
        return True
    if pkg_of_symbol in f.star:
        return True
    return False

for f in files:
    local_decl = {n for n, fs in all_decl_names.items() if f.rel in fs}
    ids = set(re.findall(r"(?<![\w.])[A-Za-z_]\w*\b", f.src))
    for name in ids:
        if name in top_types and name[0].isupper():
            pkgs = top_types[name]
            if name in local_decl and f.pkg in pkgs:
                continue
            if any(visible(f, p, name) for p in pkgs):
                continue
            # puede ser un tipo anidado o un miembro con el mismo nombre
            if re.search(r"\b(?:class|object|interface|enum class)\s+" + name + r"\b", f.src):
                continue
            problems.append(f"{f.rel}: usa {name} sin importarlo (declarado en {sorted(pkgs)[0]})")
    # funciones de primer nivel llamadas sin punto
    for name, pkgs in top_funs.items():
        if name in local_decl:
            continue
        if re.search(r"(?<![\w.])" + re.escape(name) + r"\s*\(", f.src):
            if not any(visible(f, p, name) for p in pkgs):
                problems.append(f"{f.rel}: llama a {name}(...) sin importarla (declarada en {sorted(pkgs)[0]})")
    for name, pkgs in top_ext.items():
        if re.search(r"\.\s*" + re.escape(name) + r"\s*[({]", f.src):
            if name in local_decl and f.rel in all_decl_names[name]:
                continue
            if not any(visible(f, p, name) for p in pkgs):
                # puede ser un método miembro con el mismo nombre; se avisa igualmente para revisión manual
                problems.append(f"{f.rel}: usa la extensión .{name}(...) sin importarla (declarada en {sorted(pkgs)[0]}) [revisar]")

# ---- 2) símbolos de librerías importados en otros archivos --------------------------------------------------------
lib_imports = defaultdict(set)
for f in files:
    for name, full in f.imports.items():
        if not full.startswith(BASE + "."):
            lib_imports[name].add(full)

SKIP_LOWER_PLAIN = {"remember", "getValue", "setValue", "provideDelegate",
                    # existen también en la biblioteca estándar / como miembros
                    "map", "first", "launch", "catch", "update", "edit", "filter", "forEach", "collect", "flatMap",
                    "Map", "List", "Set",
                    # métodos Java (ByteArrayOutputStream.size(), ZipFile.size()) y constante de enum (FieldType.URL)
                    "size", "URL"}
for f in files:
    local_decl = {n for n, fs in all_decl_names.items() if f.rel in fs}
    for name, fulls in lib_imports.items():
        if name in f.imports or name in SKIP_LOWER_PLAIN:
            continue
        if any(s.rsplit(".", 1)[0] in f.star for s in fulls):
            continue
        if name in local_decl:
            continue
        if name[0].isupper():
            if re.search(r"(?<![\w.])" + re.escape(name) + r"\b", f.src):
                # solo si no es un miembro anidado accedido con punto (p. ej. Color.White -> 'White' tiene punto delante)
                problems.append(f"{f.rel}: usa {name} sin importarlo (se importa como {sorted(fulls)[0]} en otros archivos)")
        else:
            # extensiones/funciones: .name( o name( o .name {
            if re.search(r"(?<![\w])(?:\.\s*)?" + re.escape(name) + r"\s*[({]", f.src):
                # funciones miembro con el mismo nombre que una extensión importada en otro sitio
                problems.append(f"{f.rel}: usa {name}(...) sin importarlo (se importa como {sorted(fulls)[0]} en otros archivos) [revisar]")

# ---- 4) delegados by ----------------------------------------------------------------------------------------------
for f in files:
    if re.search(r"\b(?:val|var)\s+\w+(?::\s*[\w<>?]+)?\s+by\s+(?:remember|rememberSaveable|vm\.|\w+\.collectAs|\w+\s*$)", f.src, re.M) or re.search(r"\bby\s+(?:remember|rememberSaveable)", f.src):
        uses_var = re.search(r"\bvar\s+\w+(?::\s*[\w<>?]+)?\s+by\s+(?:remember|rememberSaveable)", f.src)
        if "getValue" not in f.imports and "androidx.compose.runtime" not in f.star:
            problems.append(f"{f.rel}: usa 'by' pero falta import androidx.compose.runtime.getValue")
        if uses_var and "setValue" not in f.imports and "androidx.compose.runtime" not in f.star:
            problems.append(f"{f.rel}: usa 'var ... by remember' pero falta import androidx.compose.runtime.setValue")

# ---- 5) propiedades de extensión de unidades (1.dp, 12.sp) ---------------------------------------------------------
for f in files:
    for unit in ("dp", "sp"):
        if re.search(r"[\w)]\." + unit + r"\b", f.src):
            if unit not in f.imports and "androidx.compose.ui.unit" not in f.star:
                problems.append(f"{f.rel}: usa .{unit} sin import androidx.compose.ui.unit.{unit}")

if problems:
    seen = set()
    print(f"{len(problems)} posible(s) problema(s):")
    for p in problems:
        if p not in seen:
            print(" -", p)
            seen.add(p)
    sys.exit(1)
print("OK: imports coherentes en", len(files), "archivos.")
