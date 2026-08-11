# Crossfeed

Any music link, your player. Android. Tier 0 (link router) + Tier 1 (unified library).

A friend sends a Spotify link. You use Apple Music, and half the tracks are already
FLAC on your phone. Crossfeed sits between the tap and the app: it reads the link,
works out what the song actually is, finds it where *you* listen, and opens it there.

## Status

v0.2.0 — working and installed. Verified on device:

| case | result |
|------|--------|
| spotify track → apple music | exact track, ~500 ms cold, 62 ms cached |
| apple music track → local flac | matched, opened in poweramp |
| youtube music (shared text) → apple music | exact track |
| spotify playlist → apple music | no exact match, falls back to search |
| two targets selected | sheet lists both, each badged exact / search |
| library search | local files + apple catalogue merged in one list |
| artwork | spotify `visualIdentity.image[]`, mediastore thumbnails |

## How it resolves

```
link → normalize (drop ?si=, keep ?i=)
     → identify source by host
     → extract metadata from the source's own page
          spotify  __NEXT_DATA__ on the embed page
          apple    ld+json (MusicComposition / MusicRecording)
          ytmusic  og tags, watch page as fallback
          other    ld+json → og:title / og:description
     → {title, artist, album, durationMs}
     → in parallel:
          local     MediaStore, scored match
          target(s) apple: itunes catalog search, scored
                    others: the platform's own search deep link
     → local wins if "prefer my own files" is on
       one target + exact → opens straight through
       several targets    → sheet asks which one
```

Scoring is title 0.6 / artist 0.3 / duration 0.1, after normalizing away diacritics,
`(feat. …)`, `(2022 Remaster)`, `(Official Video)` and punctuation. Local needs 0.72,
catalog needs 0.55.

No Odesli, no song.link, no middleman service, no API keys. The only hosts contacted
are the source platform's own page and Apple's own catalog search.

## Routing targets

The icon wheel on the route screen shows every supported platform, drawn with its
real launcher icon; drag to spin it, tap to select.

- **one selected** → links route straight through, no interruption
- **several selected** → the sheet lists exactly those, each marked `exact` or
  `search`, numbered in selection order

Uninstalled platforms stay on the wheel, dimmed.

## Library (Tier 1)

One search box over your own files and Apple's catalogue, merged: a track you own as
FLAC *and* that exists on Apple Music is one row with both badges, not two rows.
Tapping a local row hands off to Poweramp; a catalogue row opens the target (or asks,
when several are selected). Idle state lists recently added files and a format
breakdown. Voice notes and recordings are filtered out — music only, 45 s minimum.

Apple's *catalogue* is searchable without credentials. Your Apple Music *library*
is not — that needs MusicKit and a paid developer account, so it is out of scope here.

## Look

Translucent glass over a slow-drifting gradient. Wordmark and launcher icon are
Permanent Marker — funky handwritten, a `cf` monogram on a deep teal gradient.
Body text is Geist, tight tracking, white rather than grey.

**Colour is dynamic, never fixed.** Two inputs:

- **time of day** — dawn (coral/amber), day (cyan/mint), dusk (rose/tangerine),
  night (teal/indigo). No generic violet anywhere.
- **the app you route to** — the dominant saturated colour is extracted from that
  app's own launcher icon and becomes the accent, blended 62% toward the daypart hue
  so it can never clash. Apple Music turns the app red; Spotify turns it green.

The resolve sheet uses real window blur-behind (`FLAG_BLUR_BEHIND`, API 31+) so the
app underneath is genuinely blurred, with a graceful fall back to a scrim.

## Listening history (replaces the old library tab)

Crossfeed reads the now-playing card of **any** player on the phone — Poweramp,
Spotify, Apple Music — via `MediaSessionManager` behind a notification-listener
binding. No API, no keys, no account. A play is recorded after 20 s of actual
playback, with how long you listened, not just that it started.

- **dashboard** — minutes, plays, distinct tracks, distinct artists, over
  today / week / month / all time
- **charts** — top artists, most played, genres, and which app you listened in
- **feed** — every play as a spinning disc with cover art, artist, source and
  listened-vs-total time; remove any row
- **habits** — day streak, peak listening hour, share of tracks finished, skips
- **advanced stats** (collapsible) — longest session, most-skipped track, days with
  music, average per day, biggest day, new artists, new tracks, on-repeat track,
  weekday vs weekend split, and per-app finish rate
- **storage** — real on-disk size (database + cached art) and row count, with a
  1–365 day slider: "erase anything older than N days", behind a confirmation

Tracks are **clickable** everywhere they appear — a row in "most played" or in the
feed routes that song straight into your chosen player (local file first if you own
it, otherwise an exact Apple match, otherwise a pre-filled search).

Rows update **live** — a track being played now grows its listened time in place
every 20 s rather than appearing only once it ends, and resuming the same track
within 20 minutes reuses its row instead of creating a duplicate. Cover art is read from the embedded bitmap first and cached as a small private JPEG —
Spotify hands out a `content://` URI owned by its own process that another app cannot
read, so preferring the URI silently loses its artwork.
Genres are backfilled lazily from Apple's catalogue for tracks that lack them.

Note: reinstalling the app unbinds Android's notification listener, so Crossfeed
calls `requestRebind` on every launch.

**Stats are the absolute record.** They are not computed from the feed. Every play
writes into separate aggregate tables (`agg` keyed by kind/label/day, `finishes`,
`meta`) as you listen, so erasing the feed frees space without losing a single
number — minutes, charts, streaks and habits all survive.

**Only music counts.** Known music players are always allowed; YouTube, Netflix,
Prime, Hotstar, JioCinema, Disney+, MX Player, VLC, Kodi, Twitch, Instagram, TikTok
and messengers are blocked; every installed browser is detected at runtime and
blocked. Anything else needs an artist tag and a runtime under 45 minutes.

**Privacy:** `listening.db` lives in the app's private data directory. No other app
can read it, and none of it leaves the device unless you turn sharing on or export it
yourself.

## Export

`listening → export → export .xlsx` writes a real spreadsheet, built by hand — zip
plus OOXML, no library. Eleven sheets: every play with timestamps and finish ratio, then
totals by day, track, artist, album, app, hour and skip count, plus genres, per-app
finish rates and a summary. Saved straight to **Downloads** on Android 10+, and a
**send** button hands it to any app through a private FileProvider.

## Builds

```sh
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew installDebug     # dev.crossfeed.debug
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleRelease   # dist/crossfeed-0.1.0.apk
```

Debug installs as `dev.crossfeed.debug` and carries the server-address box. Release is
`dev.crossfeed`, signed with the local debug keystore so it installs straight from the
APK, and the server box is compiled out — it uses the shipped address.

## auxshare (the social tab)

Sign-up is a handle. No password, no email, no phone number — the phone holds a bearer
token, the server holds only its hash.

- **status row** — a stories-style strip across the top: everyone playing something in
  the last 20 minutes, their cover art in a ring. Tap one and it opens in your player.
- **the feed** — what your people are playing, **friends only**, never your own listens.
  A compact preview of four sits in the tab; **open the feed** goes full screen as a
  three-column grid of cover art: **double tap to love**, **hold** for the other
  reactions, **tap** for a play / save overlay. It pages forever as you scroll.
- **posting is automatic** — anything you play past 30 seconds with sharing on becomes a
  post, and continued listening updates that same post rather than making a new one.
  There is no compose button by design; the only control is the sharing toggle.
- **sharing is off until you turn it on** — and it posts one track at a time, never
  your history. Your own posts can be deleted from the feed.
- **your people** — add by handle; a matching pending request auto-accepts. The feed is
  you plus accepted friends, nobody else.

A handle is permanent. The only way to change it is to wipe the account and start over,
so the sign-up card says so before you commit.

## nearby

A radar circle that sweeps for other Crossfeed users. Dots sit by distance, tap one to
send a connection request; green means already connected. Location is **rounded to
about 110 m before it is sent**, only while the broadcast toggle is on, and only
broadcasting users seen in the last 15 minutes appear. Turning it off, or *wipe my
account*, clears the stored position on the server.

## crossplay

Cross-platform listening sessions. One person starts one and gets a 6-character code
and a join link; anyone can add to the shared queue by searching Apple's catalogue,
the host decides what plays. Everyone hits **play in my app** and hears it in their
own player — Spotify on one phone, Apple Music on another, a local FLAC on a third.
The join link opens a small landing page that deep-links `crossfeed://join/CODE`.

## Backend

Cloudflare Workers + D1, in `server/` — plain JS, no framework, no third-party service.
Deploy steps and the endpoint table are in `server/README.md`.

**Deployed and live** at `https://crossfeed-api.tiny-violet-c3ae.workers.dev`
(D1 database `crossfeed`, 8 tables). The app ships pointing at it.

The server address box appears in **debug builds only** — release builds use the
compiled-in address and never show it, since by then the domain is fixed.

The base URL is **swappable from inside the app** (auxshare → server), so the
`workers.dev` host can be replaced with `crossfeed.live` later without touching a line
of code; accounts and tokens live in D1 and carry over. Only `https://` is accepted.

Security-reviewed before deploy — see `server/README.md` for the posture. The findings
that mattered: crossplay had no membership check (a forwarded invite link was a
permanent skeleton key), `/v1/nearby` returned an exact distance and so could be
trilaterated by faking your own position, and the join page interpolated an
attacker-controlled session name straight into HTML. All fixed, along with a real
account wipe, row and length caps, and https-only remote artwork.

**Sharing is opt-in and stays off** until the toggle is pressed — registering a handle
does not switch it on.

## Entry points

- **Link interception** — `ResolveActivity` claims the music hosts, but Android gives
  a *verified* owner first refusal: Spotify verifies `open.spotify.com`, so while its
  own link handling is on, Crossfeed is never offered. The route screen now detects
  exactly which installed apps are claiming which hosts and offers a **fix** button
  straight to that app's "Open by default" screen. Turn theirs off, then turn
  Crossfeed's supported links on.
- **Share sheet** — share any text containing a music link to Crossfeed. Zero setup.
- **Paste** — the "try a link" box on the route screen.

## Build

```sh
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew installDebug
```

Debug installs as `dev.crossfeed.debug` so it can sit next to a release build.

## Not yet

Playlist sync, push notifications, `crossfeed.live` itself. See `brainstorm.md`.
