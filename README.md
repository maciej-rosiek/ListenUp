# ListenUp

[![License: GPL v3](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE)
[![GitHub Release](https://img.shields.io/github/v/release/maciej-rosiek/ListenUp)](https://github.com/maciej-rosiek/ListenUp/releases)

ListenUp is a podcast player for Android that finds and skips dynamically inserted ads on the phone, with no
server. It is a fork of [AntennaPod](https://github.com/AntennaPod/AntennaPod) and keeps everything AntennaPod
does.

## What ListenUp adds

- **Ad detection per podcast.** Off by default, switched on in the podcast settings (gear icon → Ad detection).
- **Ad skipping.** Ads show as green marks on the seek bar and the mini player. Playback jumps over them with a
  soft whoosh and a snackbar to undo the skip or never skip that ad again.
- **Ad list per episode.** From the player menu: play, keep, edit or delete any ad, or run detection again.
- **Mark all as played** for several podcasts at once, from the multi-select in Subscriptions.
- **Episode limit of 1, 2 or 3** per podcast, next to AntennaPod's larger limits.

ListenUp only talks to the feeds and audio hosts of your podcasts and to podcast search, same as AntennaPod.

## How ad detection works

ListenUp tries these in order and stops once the ads it found explain the whole file: the file length minus the
ads matches the episode length in the feed, to within 4 seconds.

1. **Host marks.** Megaphone marks the ads it inserts: each ad clip starts with an encoder tag frame that holds
   the clip's length, and the audio response lists every ad slot in an `x-megaphone-payload-2` header. ListenUp
   reads both from the download it already makes.
2. **Known ads.** Ad clips found before are kept in a library and searched for in every new episode.
3. **Comparing downloads.** Hosts stitch ads into the MP3 at download time without re-encoding the show, so the
   show's MP3 frames are byte-identical in every download and only the ads differ. ListenUp downloads the episode
   again 2 to 5 times in parallel, on Wi-Fi only, hashing the stream instead of storing it. It aligns the frame
   sequences and marks frames of the played file that are not shared by every copy as ads. New ad clips go into
   the known-ads library.

The detector is plain Java behind an interface, so further detectors (for example an on-device AI one) can be
added next to it.

## Install

Download the APK from [Releases](https://github.com/maciej-rosiek/ListenUp/releases), or add
`https://github.com/maciej-rosiek/ListenUp` to [Obtainium](https://github.com/ImranR98/Obtainium) to get updates.
The app id is `com.listenup`, so it installs next to AntennaPod.

## Building

Build like AntennaPod, see its [instructions](CONTRIBUTING.md): `./gradlew :app:assembleFreeDebug`. Notes for
working on the fork (branches, upstream merges, releases, the emulator) are in
[LISTENUP_AGENTS.md](LISTENUP_AGENTS.md).

## Credits and license

- [AntennaPod](https://github.com/AntennaPod/AntennaPod), GPL-3.0. ListenUp keeps its code, history and license,
  and regularly merges AntennaPod `develop`. Please report ListenUp problems here, not to AntennaPod.
- [TrimPlayer](https://github.com/yonatanholdings/TrimPlayer), GPL-3.0: the segment overlay of the mini player
  (`SegmentOverlayView`) and the segment wash of the seek bar.

ListenUp is licensed under the GNU General Public License v3.0, see [LICENSE](LICENSE).
