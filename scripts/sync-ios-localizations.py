#!/usr/bin/env python3
"""Keep the iOS language declaration aligned with shipped Compose translations."""

import pathlib
import plistlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
RESOURCES = ROOT / "composeApp/src/commonMain/composeResources"
PLIST = ROOT / "iosApp/iosApp/Info.plist"


def supported_languages(resources):
    languages = set()
    for strings in resources.glob("values*/strings.xml"):
        qualifier = strings.parent.name
        if qualifier == "values":
            languages.add("en")
            continue
        match = re.fullmatch(r"values-([a-z]{2,3})(?:-r([A-Z]{2}|[0-9]{3}))?", qualifier)
        if not match:
            raise ValueError(f"Unsupported translation folder: {qualifier}")
        language, region = match.groups()
        languages.add(language + (f"-{region}" if region else ""))
    if "en" not in languages:
        raise ValueError("Missing base English strings.xml")
    return ["en"] + sorted(languages - {"en"})


def sync(resources, plist):
    languages = supported_languages(resources)
    # Read bytes to preserve the file's existing line endings and formatting.
    original = plist.read_bytes()
    document = plistlib.loads(original)
    if document.get("CFBundleLocalizations") == languages:
        return

    text = original.decode("utf-8")
    newline = "\r\n" if "\r\n" in text else "\n"
    pattern = r"(?m)^([ \t]*)<key>CFBundleLocalizations</key>\s*<array>.*?</array>"
    matches = list(re.finditer(pattern, text, re.DOTALL))
    if len(matches) != 1:
        raise ValueError("Expected exactly one CFBundleLocalizations array in Info.plist")
    match = matches[0]
    indent = match.group(1)
    replacement = newline.join([
        f"{indent}<key>CFBundleLocalizations</key>",
        f"{indent}<array>",
        *(f"{indent}\t<string>{language}</string>" for language in languages),
        f"{indent}</array>",
    ])
    updated = (text[:match.start()] + replacement + text[match.end():]).encode("utf-8")
    expected = dict(document, CFBundleLocalizations=languages)
    if plistlib.loads(updated) != expected:
        raise ValueError("Updating languages unexpectedly changed other plist values")
    plist.write_bytes(updated)
    print("Updated iOS languages: " + ", ".join(languages))


def main():
    try:
        sync(RESOURCES, PLIST)
        return 0
    except (ValueError, OSError, plistlib.InvalidFileException) as error:
        print(str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
