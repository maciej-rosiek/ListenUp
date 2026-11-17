#!/usr/bin/env bash
set -euo pipefail

DEVICE="adb -s ${ANDROID_SERIAL:-emulator-5554}"

dump() {
    $DEVICE shell uiautomator dump /sdcard/ui.xml > /dev/null
    $DEVICE shell cat /sdcard/ui.xml | tr '>' '\n' \
        | grep -o '\(text\|content-desc\)="[^"]\+"\|bounds="[^"]*"' | paste - - | grep -v '^bounds'
}

tap() {
    local line
    line="$(dump | grep -F -- "$1" | head -1)"
    if [[ -z "$line" ]]; then
        echo "not found: $1" >&2
        exit 1
    fi
    set -- $(echo "$line" | grep -o '\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]' | tr '[],' '   ')
    $DEVICE shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
    echo "tapped $line"
}

case "${1:-}" in
    dump) dump ;;
    tap) tap "${2:?text or content-desc to tap}" ;;
    screenshot) $DEVICE exec-out screencap -p > "${2:?output png path}" ;;
    *) echo "usage: $0 dump | tap <text> | screenshot <file.png>" >&2; exit 1 ;;
esac
