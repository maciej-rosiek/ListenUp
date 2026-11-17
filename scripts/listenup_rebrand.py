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


def name_of(element):
    return re.search(r'name="([^"]+)"', element).group(1)


def elements_by_name(strings_file):
    return {name_of(match.group(0)): match.group(0)
            for match in ELEMENT.finditer(strings_file.read_text(encoding="utf-8"))
            if 'name="listenup_' not in match.group(0)}


def write(folder_name, elements):
    folder = TARGET / folder_name
    folder.mkdir(exist_ok=True)
    rebranded = [element.replace("AntennaPod", "ListenUp") for element in elements]
    (folder / OUTPUT_NAME).write_text(HEADER + "\n".join(rebranded) + "\n</resources>\n", encoding="utf-8")


def main():
    for old in TARGET.glob("values*/" + OUTPUT_NAME):
        old.unlink()
    defaults = elements_by_name(SOURCE / "values" / "strings.xml")
    overridden_names = set()
    for strings_file in sorted(SOURCE.glob("values-*/strings.xml")):
        elements = [element for name, element in elements_by_name(strings_file).items()
                    if "AntennaPod" in element and name in defaults]
        if elements:
            overridden_names.update(name_of(element) for element in elements)
            write(strings_file.parent.name, elements)
    overridden_names.update(name for name, element in defaults.items() if "AntennaPod" in element)
    write("values", [defaults[name] for name in sorted(overridden_names)])


if __name__ == "__main__":
    main()
