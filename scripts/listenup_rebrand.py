#!/usr/bin/env python3
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parent.parent
SOURCE = ROOT / "ui" / "i18n" / "src" / "main" / "res"
TARGET = ROOT / "app" / "src" / "main" / "res"
OUTPUT_NAME = "listenup_rebrand.xml"
ELEMENT = re.compile(r"[ \t]*<(string|plurals)\b[^>]*>.*?</\1>", re.S)
HEADER = ('<?xml version="1.0" encoding="utf-8"?>\n'
          '<resources xmlns:tools="http://schemas.android.com/tools"'
          ' xmlns:xliff="urn:oasis:names:tc:xliff:document:1.2">\n')


def rebrand(strings_file):
    elements = [match.group(0) for match in ELEMENT.finditer(strings_file.read_text(encoding="utf-8"))
                if "AntennaPod" in match.group(0) and 'name="listenup_' not in match.group(0)]
    return [element.replace("AntennaPod", "ListenUp") for element in elements]


def main():
    for old in TARGET.glob("values*/" + OUTPUT_NAME):
        old.unlink()
    for strings_file in sorted(SOURCE.glob("values*/strings.xml")):
        elements = rebrand(strings_file)
        if not elements:
            continue
        folder = TARGET / strings_file.parent.name
        folder.mkdir(exist_ok=True)
        (folder / OUTPUT_NAME).write_text(HEADER + "\n".join(elements) + "\n</resources>\n", encoding="utf-8")


if __name__ == "__main__":
    main()
