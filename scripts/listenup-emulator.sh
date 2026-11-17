#!/usr/bin/env bash
set -euo pipefail

SDK="${ANDROID_HOME:-$HOME/android-sdk}"
AVD="${LISTENUP_AVD:-listenup_pixel8pro}"
LOG="${LISTENUP_EMULATOR_LOG:-/tmp/listenup-emulator.log}"

library_path=""
for package in libbsd libxkbfile xorg.libX11 xorg.libxcb xorg.libXext xorg.libXrender xorg.libXi xorg.libXfixes \
        xorg.libXcursor xorg.libXrandr xorg.libXdamage xorg.libXcomposite xorg.libXtst libGL libpulseaudio libdrm \
        nss nspr expat zlib libuuid alsa-lib fontconfig freetype dbus libxkbcommon wayland vulkan-loader; do
    out="$(nix build --no-link --print-out-paths "nixpkgs#$package^out" 2>/dev/null || true)"
    [[ -d "$out/lib" ]] && library_path="$library_path:$out/lib"
done

window_flag="-no-window"
[[ "${1:-}" == "--window" ]] && window_flag=""

ANDROID_HOME="$SDK" ANDROID_SDK_ROOT="$SDK" LD_LIBRARY_PATH="${library_path#:}" \
    nohup "$SDK/emulator/emulator" -avd "$AVD" $window_flag -gpu swiftshader_indirect \
    -no-snapshot-save -no-boot-anim -no-audio > "$LOG" 2>&1 &

until [[ "$(adb -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
    sleep 3
done
echo "emulator-5554 booted, log in $LOG"
