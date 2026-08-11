# Crossfeed — brainstorm

_Started 2026-08-05. Name locked: **Crossfeed**. Domain: **crossfeed.live** (available at time of writing)._
_v0.2 built and verified on device: Tier 0 (link router) + Tier 1 (unified library) — see `README.md`._
_UI is deliberately **glass**, not the flat editorial house style — decided 2026-08-06._
_Colour is dynamic (daypart x routed-app icon), wordmark is handwritten. No fixed brand purple._
_Library pivoted to a private on-device **listening history**; social feed is next._

Cross-platform music links + one library view + social listening.
Problem owner uses: **Apple Music** (primary, hi-res lossless), **Spotify**, **Poweramp** (local/offline library). Friends split across Spotify + Apple Music.

---

## 1. The actual problems

Four distinct pains, often lumped together. Worth keeping separate — they have different difficulty and different competitors.

| # | Problem | Today's workaround | Pain |
|---|---------|--------------------|------|
| P1 | Friend sends a Spotify link, I'm on Apple Music | Copy title → search manually | High, daily |
| P2 | My playlists live in 3 places, none authoritative | Manual re-adding, Soundiiz once a year | High, chronic |
| P3 | My local Poweramp library is invisible to everything | Nothing. It's a dead zone | Medium, but nobody solves it |
| P4 | No shared listening culture across platform lines | Screenshots in group chat | Medium, but this is the fun part |

P3 is the wedge. Every competitor treats "your music" as "your streaming accounts." Nobody treats a local FLAC folder as a first-class library.

---

## 2. What already exists

### Link translation (P1)
- **Odesli / song.link** — the incumbent. Paste a link → landing page with buttons for every platform. Free, has an API. Weaknesses: it's a *web landing page*, not a router. You still tap through. No memory of your preferred platform. Built for artists marketing releases, not friends sharing songs.
- **SongPort, Listen.lt, Amplify.link, NotNoise, Feature.fm, Soundplate Clicks** — same shape, artist-marketing focused (pre-saves, retargeting pixels, analytics). None are consumer share tools.
- **Gap:** nobody does *silent* redirection. There is no "I'm an Apple Music person, any link anyone sends me opens in Apple Music, no interstitial."

### Playlist transfer (P2)
- **Soundiiz** — 40+ services, widest support, has ongoing sync. Free tier = 1 playlist at a time. Subscription.
- **TuneMyMusic** — simplest, 20+ platforms, free up to 500 tracks. One-shot transfers.
- **SongShift** (iOS) — nicest mobile UX, manual match-review step.
- **FreeYourMusic**, **Tuneferry**, **PlaylistGo**, **Stamp**.
- **Gap:** all are *transfer* (one-time copy, A→B), not *sync* (bidirectional, continuous, conflict-aware). Soundiiz "sync" is paid and one-directional. And none include local files on either side.

### Social listening (P4)
- **Airbuds** — the big one. Home-screen widget showing friends' now-playing across Spotify/Apple Music/SoundCloud. Emoji/sticker reactions, built-in chat, listening stats. 15M downloads, 5M MAU, 1.5M DAU, $5M raised. Founded late 2022.
- **Last.fm** — scrobbling since 2002. Universal, works with anything including local players (Poweramp scrobbles to it). Social layer is ancient and dead-ish, but the data layer is the standard.
- **stats.fm** — Spotify-only stats/wrapped-anytime.
- **Musicboard / Album of the Year / RateYourMusic / Crate / Kora** — "Letterboxd for music": ratings, reviews, lists. Async and critical, not real-time and social.
- **Spotify Jam / Stationhead / Turntable.fm** — synchronous co-listening, single-platform.
- **Gap:** Airbuds owns real-time now-playing. But nobody connects *reaction → action*. You react to a friend's song with 🔥 and… nothing happens. It doesn't land in your library, on your platform.

### Unified library / local files (P3)
- **Plexamp / Navidrome+Symfonium / Jellyfin** — excellent for self-hosted local libraries, zero streaming-service awareness.
- **Poweramp** — best local Android player, no cloud/social anything.
- **Gap:** total. No product presents "my music" as streaming + local in one index.

### The one-sentence positioning
> Odesli routes links but doesn't know you. Soundiiz moves playlists but doesn't watch them. Airbuds shows what friends play but you can't act on it. Plexamp knows your files but nothing else. **Crossfeed is the layer that knows all four.**

---

## 3. Name

Chosen: **Crossfeed** · **crossfeed.live**
- Real audio term (headphone DSP that blends L/R — an audiophile signal, matches the lossless angle)
- Reads as *cross*-platform + social *feed*
- Portmanteau-adjacent, same register as Tamepest
- Domain/handle likely gettable, not a band or a big product

Alternates:
- **Segue** — DJ/radio term for a smooth transition between tracks. Perfect metaphor for platform handoff; also means conversational transition, so it covers the social half. Clean, short, real word. Risk: common word, SEO-hostile.
- **Earshot** — "within earshot" = hearing what those near you hear. Great for the social product, weak for the link-router.
- **Auxpass** / **Aux** — passing the aux cord = the universal cultural act of sharing music. Fun, young, memorable. Risk: reads as a party/queue app, not infrastructure.
- **Interlude**, **Crossfade**, **Mixdown**, **Overhear**, **Ampersand**.

Feature naming inside the app can steal the good rejects: the link router is *Segue*, the friend feed is *Earshot*, the shared queue is *Aux*.

---

## 4. Feature map

### Tier 0 — the wedge (must nail this or nothing else matters)
**Universal link resolution, zero-friction.**
- Register Crossfeed as a handler for `open.spotify.com`, `music.apple.com`, `music.youtube.com`, `tidal.com`, `soundcloud.com`, `deezer.com`, `bandcamp.com`, `song.link`.
- Friend's Spotify link → tapped → resolves → **opens straight in Apple Music**. No landing page, no tap-through. That's the whole magic trick.
- Android: intent filter, "open by default" for those hosts. This works today and is genuinely easy.
- iOS: Universal Links can't be hijacked for another app's domain. Workarounds: share-sheet extension ("Share → Crossfeed"), keyboard extension, Shortcuts automation, or the app's own `cf.link/xyz` domain when *sending*. iOS is the hard side.
- Outbound: share a track from Crossfeed → produces a `crossfeed.link/...` URL that resolves per-recipient (their platform, their app).
- Resolution backbone: **ISRC**. Spotify returns it in `external_ids`, Apple Music exposes `Song.isrc`, MusicBrainz/Deezer carry it. ISRC match = near-perfect. Fallback chain: ISRC → UPC+track# → normalized artist/title/duration fuzzy → AcoustID fingerprint (for local files).
- Cache every resolution. The resolution graph *is* the moat and it compounds per user.

### Tier 1 — one library
- Read-only unified index: Apple Music library + Spotify library + local files (Poweramp folder / SAF tree / MediaStore).
- Universal search across all three at once. Result rows show which sources have it, at what quality.
- "Where is this best?" — surfaces that a track exists as local FLAC 24/96, Apple Music ALAC, and Spotify 320 OGG, and hands off to the right player.
- Dedup by ISRC + fingerprint so one song isn't three rows.
- Playback: **do not build a player.** Deep-link out to Apple Music / Spotify / Poweramp. Fighting three DRM stacks and losing hi-res is a trap. Crossfeed is an index and a router, not a player. (Possible exception later: play *local* files in-app so at least one source is native.)

### Tier 2 — sync
- Playlists as a **platform-neutral document** (the "score"), with per-platform projections. Not "copy A to B" — one canonical playlist, mirrored everywhere, continuously.
- Conflict UI: track added on Spotify while removed on Apple → surface, don't guess.
- Unmatchable tracks get a visible "unmatched" shelf instead of silently vanishing (the #1 complaint about transfer tools).
- Local-file playlists included — a local FLAC that exists nowhere on streaming stays in the canonical doc and is skipped/marked in the streaming projections.
- Bidirectional sync is genuinely hard. v1 could be "one canonical source per playlist, push to others, detect drift."

### Tier 3 — social
- **Feed of now-playing / recently-played across platforms.** Requires scrobbling: Spotify has `recently-played`, Apple Music has recent-played endpoints, Poweramp broadcasts track-change intents on Android. Last.fm can be the ingest path for anything exotic.
- **Reactions that do something.** React 🔥 → the track lands in *your* "from friends" playlist, on *your* platform, resolved automatically. This is the differentiator vs Airbuds — the reaction is a real action.
- **Cross-platform shared playlists.** A playlist a Spotify friend and an Apple Music friend both edit natively from their own app. Nobody has this. It might be the single most compelling feature in the whole document.
- **Send a song to a person, not a platform.** "Send to Priya" → lands correctly in her world.
- Mixtape objects: an ordered, annotated, dated set of tracks with a note per track. Shareable as a link that plays anywhere.
- Listening rooms / co-listen: interesting, but sync playback across DRM platforms is basically impossible. Skip. "Same playlist, own time" is the realistic version.
- Weekly digest: what your friends played that you'd have liked.

### Tier 4 — audiophile / power-user (matches primary use of Apple Music for lossless)
- Quality badges per source (ALAC 24/192 vs OGG 320 vs local FLAC) — instantly shows where a track is best.
- "Own it, don't rent it" — flag streaming favorites that are cheap to buy on Bandcamp/Qobuz, or that already exist locally.
- Library health: duplicates, dead tracks removed from streaming catalogs, local files missing tags, tracks in playlists that went grey.
- Export everything as portable JSON/M3U. Anti-lock-in is the brand.

---

## 4b. Standing decision: own stack, no middlemen

**No third-party APIs.** Not Odesli, not song.link, not any smart-link service, no API
keys, no vendor that can rate-limit us or disappear. Resolution is ours end to end.

That is possible because a share link already carries its own metadata: the source
platform's public page states the title, artist and duration in `ld+json` and Open
Graph tags — the same data any chat app reads to draw a preview. Read that, and the
song stops being "a Spotify link" and becomes `{title, artist, durationMs}`, which is
platform-neutral and matchable anywhere.

The only outbound calls, therefore, are:
1. the source platform's own page (what the link points at), and
2. the destination platform's own search (Apple's catalog search for exact Apple
   matches; every other platform gets a search deep link, no call at all).

Verified working 2026-08-05 against Spotify, Apple Music and YouTube Music. Consequence
worth stating plainly: exact matching **into** Apple Music works; exact matching into
Spotify is impossible without Spotify credentials, so Spotify targets land on Spotify's
search screen. Given Apple Music is the primary listening platform, the important
direction is the exact one. This also neatly sidesteps §5's Spotify wall entirely.

When `crossfeed.live` exists, the same resolver runs in a Worker — our own API, not
someone else's.

## 5. Feasibility — the part that decides everything

### Spotify: this is the wall
As of 2025-05-15 Spotify only accepts **Extended Quota Mode** applications from registered businesses with a launched service and **250k+ MAU**. Catch-22: can't reach 250k without extended access, can't get extended access without 250k. Feb 2026 changes tightened Development Mode further; July 2026 update raised client IDs to 25 per developer but made quota shared per developer account. Development Mode is limited to a small allowlist of users (~5) and requires the app owner to have Premium.

**Implication — pick one:**
- **(A) BYO-credentials / self-host.** Each user creates their own Spotify developer app, pastes their own client ID. Their own dev-mode allowlist covers themselves. Legitimate, zero quota risk, and it's exactly how the self-hosted music world (Navidrome, Symfonium) already operates. Ceiling: technical users only. **This is the right v1** given the app starts as a personal tool for a handful of friends.
- **(B) Avoid the Spotify API entirely for identity.** Use Spotify only as a *link target* (deep links need no API) and get listening data from Last.fm scrobbles, which the user's Spotify account can already feed. Surprisingly viable — sidesteps the wall completely for the social feature.
- **(C) Odesli's API** for the link-translation half. Free tier exists, no Spotify auth needed at all.
- (D) Apply for extended quota. Not realistic pre-launch.

**Design consequence:** architect so that Spotify-API-dependent features degrade gracefully to link-only + Last.fm. Don't let the whole product die on one vendor's policy.

### Apple Music
Friendlier. MusicKit + Apple Music API let a third-party app, with user permission, read the user's library, **create playlists, add to library**, and play catalog content. `Song.isrc` is exposed. Search offset capped at 75; batch limits 100 songs / 10 albums / 5 artists per query; no formal rate limit as of early 2026. Costs $99/yr Apple Developer Program. MusicKit JS makes a web version real.
Caveat: playback of hi-res lossless happens in Apple's player, not yours — another reason to route rather than play.

### Local / Poweramp
- Android: read the library via MediaStore or a SAF folder tree; tags via ffmpeg/taglib; fingerprint via Chromaprint → AcoustID → MusicBrainz → ISRC. That closes the loop back to streaming identity.
- Poweramp broadcasts track-change intents → free now-playing scrobbles.
- Handoff to Poweramp by file URI is straightforward.

### YouTube Music / Tidal / Deezer / SoundCloud
Deezer and SoundCloud have usable public APIs. Tidal has a developer API. YouTube Music has no official API (unofficial libs only). Support them as link *targets* first, accounts later.

### Legal/ToS
Reading user libraries with OAuth consent is sanctioned by both Apple and Spotify. Cross-platform playlist sync is exactly what Soundiiz/SongShift do openly, so it's established practice. Lines not to cross: no audio proxying, no catalog scraping/redistribution, no stream-ripping. Stay an index + router.

---

## 6. Form factor

Reality: the killer link-interception feature is **Android-native only**. iOS can't silently take over another app's links.

- **Android app** — full power. Intent interception, Poweramp/local files, MediaStore, share sheet, home-screen widget for the friend feed. This is where the vision actually works, and the user is already an Android/Poweramp person.
- **Web** — the universal fallback and the sharing surface. `crossfeed.link/xyz` must open in a browser for anyone with no app installed and do the right thing (or at least an Odesli-style chooser). Also the natural home for playlist sync management on a big screen. Cloudflare Workers + D1/KV fits, matching the SlideForge setup.
- **iOS** — share-sheet extension + Shortcuts. Degraded but usable. Later.

**Suggested v1 shape:** Android app + a thin Cloudflare Worker for link resolution and the share domain. The Worker is also the only piece friends on other platforms need.

---

## 7. Staged plan

**v0 — link router — BUILT 2026-08-05**
Android app, no accounts, no keys. Intercepts music links from the big platforms plus
the share sheet, extracts metadata from the source page, matches against the local
library and the target catalog in parallel, opens the winner. Settings = target
platform, prefer-local, auto-open. Measured: ~500 ms cold, 62 ms cached.
Local FLAC match hands off to Poweramp.

**v1 — one library — BUILT 2026-08-06**
Local-file indexing + Apple *catalogue* search in one merged list, quality badges,
handoff to Poweramp or the chosen platform. Multi-target routing: one app selected
routes silently, several make the sheet ask.
Scope correction worth recording: the Apple Music **library** (what you saved) needs
MusicKit and the paid developer account, so v1 covers the **catalogue** (public,
credential-free) plus your local files. The unified *library* in the original sense
stays blocked on that $99/yr decision — worth making deliberately, not by default.

**v2 — sync**
Canonical playlist documents, projections to each platform, drift detection, unmatched shelf.

**v3 — social**
Accounts, friends, cross-platform now-playing feed, actionable reactions, cross-platform shared playlists, mixtapes.

Note the ordering is deliberate: each stage is independently useful, and the social layer — the hardest to get right and the only one needing network effects — comes last, on top of a resolution graph that's already been battle-tested by then.

---

## 8. Open questions

- ~~Odesli or own resolver?~~ Settled: own, see §4b. Remaining version of the question — page markup changes without notice, so how do we notice a broken extractor before the user does? (A handful of known-good links as a self-test, run on demand.)
- ISRC is still the ideal key and none of the source pages expose it reliably. Worth a second pass: MusicBrainz is open data rather than a vendor API, which may fit the no-middlemen rule.
- Is Last.fm the right universal ingest layer for now-playing, or a legacy crutch?
- Bidirectional sync: worth it, or is "one owner per playlist + drift alerts" 90% of the value for 20% of the work?
- Social layer needs friends to install it. Is there a version where a Spotify-only friend participates with **zero install** — purely via `crossfeed.link` pages and web? That would be the growth unlock.
- Monetization, if ever: audiophile/power-user subscription for sync (Soundiiz charges for exactly this), or self-host + free forever?
- Multi-user Spotify story if it ever outgrows BYO-credentials. There may not be one, and that's worth knowing early.

---

## 9. Sources

- [Odesli / Songlink guide](https://orphiq.com/resources/songlink-guide) · [smart link comparison](https://songport.link/blog/best-free-music-smart-links)
- [Spotify: Updating the Criteria for Web API Extended Access](https://developer.spotify.com/blog/2025-04-15-updating-the-criteria-for-web-api-extended-access) · [Feb 2026 Dev Mode migration guide](https://developer.spotify.com/documentation/web-api/tutorials/february-2026-migration-guide) · [July 2026 quota updates](https://developer.spotify.com/blog/2026-07-23-web-api-quota-updates)
- [Apple MusicKit](https://developer.apple.com/musickit) · [Song.isrc](https://developer.apple.com/documentation/musickit/song/isrc)
- [Airbuds — TechCrunch](https://techcrunch.com/2025/09/17/airbuds-is-the-music-social-network-apple-and-spotify-wish-they-had-built/) · [Music Ally](https://musically.com/2025/09/18/social-music-app-airbuds-has-1-5m-daily-users-and-5m-of-funding/)
- [Playlist transfer tool comparison](https://www.playlistgo.io/reviews/best-playlist-transfer-tool/) · [Soundiiz vs TuneMyMusic](https://tuneferry.com/blog/tuneferry-vs-soundiiz-vs-tunemymusic-spotify-transfer)
- [Letterboxd-for-music apps](https://www.achriom.com/blog/best-letterboxd-for-music-apps/)
