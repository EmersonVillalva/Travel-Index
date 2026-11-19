#!/usr/bin/env python3
"""Comprueba las librerías nativas (.so) de un APK para el requisito de Google Play de páginas de 16 KB
(apps que apuntan a Android 15 o superior; obligatorio para subir actualizaciones desde el 1 de febrero de 2027).
Solo importan las ABI de 64 bits (arm64-v8a y x86_64). Para cada .so se mira:
  1) que todos sus segmentos LOAD del ELF estén alineados a 16 KB o más;
  2) si va sin comprimir en el APK, que sus datos empiecen en un múltiplo de 16 KB.
Sin ninguna librería nativa no hay nada que alinear. Sale con error si alguna librería de 64 bits no cumple.
Uso: python3 tools/check_native_libs.py ruta/al.apk"""
import struct, sys, zipfile

PAGE = 16384
ABIS_64 = ("arm64-v8a", "x86_64")


def load_aligns(data: bytes) -> list[int]:
    """p_align de cada segmento PT_LOAD de un ELF de 64 bits (little endian)."""
    if data[:4] != b"\x7fELF":
        raise ValueError("no es un ELF")
    if data[4] != 2 or data[5] != 1:
        raise ValueError("no es un ELF de 64 bits little endian")
    phoff = struct.unpack_from("<Q", data, 0x20)[0]
    phentsize, phnum = struct.unpack_from("<HH", data, 0x36)
    aligns = []
    for i in range(phnum):
        off = phoff + i * phentsize
        if struct.unpack_from("<I", data, off)[0] == 1:  # PT_LOAD
            aligns.append(struct.unpack_from("<Q", data, off + 48)[0])
    return aligns


def data_offset(raw, info: zipfile.ZipInfo) -> int:
    """Dónde empiezan los datos de una entrada dentro del archivo (tras su cabecera local)."""
    raw.seek(info.header_offset + 26)
    name_len, extra_len = struct.unpack("<HH", raw.read(4))
    return info.header_offset + 30 + name_len + extra_len


def check(apk: str) -> int:
    problems = 0
    found = 0
    with zipfile.ZipFile(apk) as z, open(apk, "rb") as raw:
        for info in z.infolist():
            parts = info.filename.split("/")
            if len(parts) != 3 or parts[0] != "lib" or not parts[2].endswith(".so"):
                continue
            abi = parts[1]
            if abi not in ABIS_64:
                continue
            found += 1
            notes = []
            try:
                aligns = load_aligns(z.read(info))
                if not aligns or min(aligns) < PAGE:
                    notes.append("segmentos ELF alineados a %s (hacen falta %d)" % (min(aligns) if aligns else "?", PAGE))
            except ValueError as e:
                notes.append(str(e))
            if info.compress_type == zipfile.ZIP_STORED and data_offset(raw, info) % PAGE != 0:
                notes.append("sin comprimir pero no empieza en un múltiplo de 16 KB dentro del APK")
            print(("FALLA  " if notes else "OK     ") + info.filename + ("  -> " + "; ".join(notes) if notes else ""))
            problems += bool(notes)
    if found == 0:
        print("Sin librerías nativas de 64 bits: nada que alinear a 16 KB.")
    elif problems:
        print("%d de %d librerías no cumplen el requisito de 16 KB." % (problems, found))
    else:
        print("Las %d librerías nativas de 64 bits cumplen el requisito de 16 KB." % found)
    return 1 if problems else 0


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit("Uso: python3 tools/check_native_libs.py ruta/al.apk")
    sys.exit(check(sys.argv[1]))
