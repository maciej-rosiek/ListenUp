# ListenUp

ListenUp is a podcast player based on [AntennaPod](https://github.com/AntennaPod/AntennaPod) that finds and
skips dynamically inserted ads on the phone, with no server.

## How ad detection works

Hosts like Megaphone stitch ads into the MP3 at download time without re-encoding the show, so the show's
MP3 frames are byte-identical in every download and only the ads differ. After an episode is downloaded,
ListenUp hashes every MP3 frame, downloads the episode again 2-5 times on Wi-Fi (hashing the stream, not
storing it), aligns the frame sequences and marks the frames of the played file that are not shared with
every other copy as ads. It fetches more copies until the file length minus the ads matches the episode
length in the feed. Ad clips it finds are kept as a known-ads library and searched for in later episodes
first.

Ad detection is off by default and switched on per podcast in the podcast settings.

## Credits and license

- [AntennaPod](https://github.com/AntennaPod/AntennaPod), GPL-3.0. ListenUp keeps its code, history and
  license and merges AntennaPod `develop`.
- [TrimPlayer](https://github.com/yonatanholdings/TrimPlayer), GPL-3.0: the skip cue sound
  (`playback/service/src/main/res/raw/skip_cue.wav`), the segment overlay of the mini player
  (`SegmentOverlayView`) and the segment wash of the seek bar.

ListenUp is licensed under the GNU General Public License v3.0, see [LICENSE](LICENSE).

## After merging AntennaPod

Run `python3 scripts/listenup_rebrand.py` to regenerate the app-level string overrides that show ListenUp
instead of AntennaPod in every language.
