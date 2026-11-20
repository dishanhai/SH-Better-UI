Three generated mono sine waves used for PCM playback tests, not extracts from recordings.

```
ffmpeg -f lavfi -i "sine=frequency=311:sample_rate=8000:duration=3" -ac 1 \
  -c:a libmp3lame -b:a 16k -write_xing 0 -map_metadata -1 -id3v2_version 0 outgoing.mp3
```

Incoming uses 523 Hz/8000 Hz; mismatch uses 577 Hz/16000 Hz.
