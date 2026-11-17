#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:?usage: scripts/listenup-release.sh <major.minor.patch>}"
TAG="v$VERSION"
KEY_DIR="${LISTENUP_KEY_DIR:-$HOME/.android/listenup-release}"
APK="app/build/outputs/apk/free/release/app-free-release.apk"
RELEASE_APK="build/ListenUp-$VERSION.apk"

if [[ ! "$VERSION" =~ ^[0-9]+\.[0-9]{1,2}\.[0-9]{1,2}$ ]]; then
    echo "Version must look like 1.2.3 with minor and patch below 100" >&2
    exit 1
fi
if [[ -n "$(git status --porcelain)" ]]; then
    echo "Commit or stash your changes first" >&2
    exit 1
fi

PASSWORD="$(cat "$KEY_DIR/password.txt")"
./gradlew :app:assembleFreeRelease \
    -PreleaseStoreFile="$KEY_DIR/listenup.jks" \
    -PreleaseStorePassword="$PASSWORD" \
    -PreleaseKeyAlias=listenup \
    -PreleaseKeyPassword="$PASSWORD" \
    -PlistenupVersionName="$VERSION"

mkdir -p build
cp "$APK" "$RELEASE_APK"
git push origin HEAD
gh release create "$TAG" "$RELEASE_APK" --title "ListenUp $VERSION" --generate-notes \
    --target "$(git rev-parse HEAD)"
git fetch --tags origin
