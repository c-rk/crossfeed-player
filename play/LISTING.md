# Crossfeed Player — Play Console pack

Everything you need to paste in. Assets sit next to this file.

| what | where | notes |
|---|---|---|
| App icon | `icon-512.png` | 512×512 PNG |
| Feature graphic | `feature-graphic-1024x500.png` | 1024×500 PNG |
| Phone screenshots | `screenshots/01…08` | 1080×1920, 9:16, use all eight in order |
| App bundle | `../dist/crossfeed-player-0.1.0.aab` | versionCode 1004 |
| Privacy policy | https://crossfeed.pages.dev/privacy.html | |
| Deletion page | https://crossfeed.pages.dev/delete.html | |

Replace `crossfeed.pages.dev` with your real Pages domain if it differs.

---

## Store listing

**App name** (30 max)

```
Crossfeed Player
```

**Short description** (80 max)

```
Open music links in your player, play your own files, see what friends play.
```

**Full description** (4000 max)

```
Somebody sends you a song. It opens in an app you don't use, or it doesn't open at all.

Crossfeed takes any music link and opens it in your player instead. It also plays the music already on your phone, quietly keeps track of what you listen to, and lets a few people you choose see what you're playing.

Four things, four tabs, about five minutes to set up.


ROUTE — links open where you actually listen

Pick the app you use and links go straight there. Spotify, Apple Music, YouTube Music, Tidal, Deezer, or your own files. Pick more than one and Crossfeed asks each time.

If you already own the song, it can play your copy instead of streaming it.


PLAYER — your own files, properly

Everything on your phone, browsable by song, album, artist, or by walking the actual folders you filed them in. Not a guess from tags — the real directory tree, with counts.

A queue you can reorder by dragging, search across the whole library, gapless playback, and a now playing screen that stays out of the way. No account, no streaming, no downloads.


LISTENING — a diary that stays on your phone

Crossfeed can note what you play in any app, not just its own player, and turn it into something worth reading: minutes, plays, top artists, genres, your streak, your peak hour, how often you actually finish a song.

Three things come out of that:

Sing along — time-synced lyrics for whatever is playing, in any app. The current line grows, the rest fade back. Words in another script come with a romanised view, so you can sing along to a Tamil or Hindi track without reading it.

Credits — who actually made the song. Writers, producers, engineers, session players. The part no streaming app shows you.

Make me a list — tell it how long you have, how familiar you want it, which genres and languages, and it builds a list that fits the time. Tap any track to open it in your own player.

None of this leaves your phone. It lives in a private database only Crossfeed can read, you can erase it whenever you like, and you can export the lot to a spreadsheet.


AUXSHARE — what your people are playing

A small feed, only the people you accept. See what they're on right now, react to it, save it for later, or play it yourself. If two of you happen to be playing the same song, you both find out.

No email, no password, no real name. Just a handle you pick. Sharing starts off and stays off until you turn it on, and turning it off stops it immediately.


WHAT IT DOESN'T DO

No adverts. No analytics. No trackers. No third-party SDKs. Your listening history and your music library never leave the device, and the only thing that ever talks to a server is auxshare, after you switch it on.
```

**Category** — Music & Audio
**Tags** — Music player, Media player, Music discovery
**Contact email** — murdawk@hypixonic.com
**Website** — https://crossfeed.pages.dev
**Privacy policy** — https://crossfeed.pages.dev/privacy.html

---

## App content declarations

**Privacy policy** — the URL above.

**App access** — *All functionality is available without special access.* Then add reviewer instructions, because the listening tab looks empty without one manual step:

```
No login, no account and no credentials are needed. Every feature works
on a fresh install.

Two features need a permission the tester grants themselves:

1. LISTENING TAB. Tap "turn on listening history". Android opens the
   notification access screen. Find "Crossfeed Player" and switch it on.
   If the switch is greyed out and says "restricted setting", that is
   Android blocking sideloaded apps: open App info, tap the three dots
   in the top corner, choose "Allow restricted settings", then return.
   Play an song in any music app for about 30 seconds and the tab fills in.

2. PLAYER TAB. Grant the audio permission when asked. The tab needs at
   least one audio file on the device longer than 45 seconds.

AUXSHARE is optional and shares nothing until a handle is created and
the sharing switch is turned on.
```

**Ads** — No, this app contains no ads.

**Content rating questionnaire** — category *Music & Audio* (not a game). Answers:

| question | answer |
|---|---|
| Violence, sexuality, language, controlled substances | No to all |
| Does the app allow users to interact or exchange content? | **Yes** — reactions and a feed shared between connected users |
| Can users share their location with other users? | **Yes** — approximate only, opt-in, shown as a distance band |
| Does it allow purchase of digital goods? | No |
| Does it contain user-generated content? | **Yes** |
| Do you provide a way to report or block users? | **Yes** — connections can be declined and removed at any time |

Expect PEGI 3 / ESRB Everyone with a "users interact" notice.

**Target audience** — 13–17 and 18+. Do **not** tick under-13, it drags in Families policy.

**Data safety** — the section below.

**Government apps** — No. **Financial features** — None. **Health** — No.

---

## Data safety form

Answer honestly here. A mismatch between this and what the app does is what gets apps pulled months later.

**Does your app collect or share any of the required user data types?** — **Yes**

**Is all user data encrypted in transit?** — **Yes** (HTTPS everywhere)

**Do you provide a way for users to request that their data be deleted?** — **Yes**, plus the URL https://crossfeed.pages.dev/delete.html

### Location
| | |
|---|---|
| Approximate location | **Collected** and **Shared** |
| Purpose | App functionality |
| Optional? | **Yes**, users can choose whether it is collected |
| Why | The "find me nearby" switch, off by default. Rounded to ~110 m and shown to others only as a distance band. |

Do **not** tick precise location.

### Personal info
| | |
|---|---|
| User IDs | **Collected**, **Shared** |
| Purpose | App functionality |
| Optional? | **Yes** |
| Why | The handle a user picks, plus a random account id. No name, email or phone number. |

Nothing else in this section. No name, no email address, no phone number.

### App activity
| | |
|---|---|
| Other user-generated content | **Collected**, **Shared** |
| Purpose | App functionality |
| Optional? | **Yes** |
| Why | Posts (track, artist, album, cover image URL, source app, listen duration), reactions, saves and connections — only while sharing is on. |

Do **not** declare "App interactions" or "Search history" — neither is sent anywhere.

### Music files and audio
- **Not collected.** The app reads local audio to play and list it, and it never leaves the device. Play's own guidance says data processed only on-device and never transmitted is not "collected".

### Everything else
Not collected: financial info, health, messages, photos, videos, contacts, calendar, files and docs, device or other IDs, crash logs, diagnostics, advertising data.

There is no analytics SDK, no crash reporter and no advertising library in the build, so there is nothing else to declare.

---

## Getting to internal testing

Order matters — Play blocks the release until the content declarations are green.

1. **Create the app.** Play Console → *All apps* → *Create app*. Name `Crossfeed Player`, language English, type **App**, **Free**. Tick the declarations.

2. **Signing — do this before your first upload.** *Test and release → Setup → App signing*. Choose **"Use an existing key"** and upload `~/.android/crossfeed-release.jks` via the PEPK tool Google links on that page (alias `crossfeed`, password in `~/.gradle/gradle.properties`).

   If you let Play generate its own key instead, anyone who sideloaded the APK has to **uninstall** to move to the Play build, losing their handle and their listening history. This is the only moment you can choose.

3. **Store listing.** *Grow → Store presence → Main store listing*. Paste the name, short and full descriptions. Upload `icon-512.png`, `feature-graphic-1024x500.png`, and all eight screenshots in order.

4. **App content.** *Policy → App content*. Work down the list: privacy policy URL, app access (paste the reviewer instructions), ads, content rating, target audience, data safety, government apps, financial features, health. Data safety takes the longest.

5. **Internal testing track.** *Test and release → Testing → Internal testing → Create new release*.
   - Upload `dist/crossfeed-player-0.1.0.aab`
   - Release name: `1004 (0.1.0)`
   - Release notes: see below
   - *Save* → *Review release* → *Start rollout to internal testing*

6. **Testers.** On the same page, *Testers* tab → create an email list → add up to 100 Google account addresses. They must be the address on their phone's Play Store. Copy the **join link** and send it to them; they open it, accept, then install from Play.

7. **Wait.** Internal testing skips the review queue. Builds are usually live within minutes, occasionally an hour or two. The join link works the moment the release finishes processing.

**Release notes**

```
First build on Play.

Route music links to the app you actually use, play the files already on
your phone, keep a private listening diary, and share what you're playing
with a few people you choose.
```

---

## Things that will trip you up

- **AAB, not APK.** New apps must ship an app bundle. The APK is only for the sideload route.
- **Identity verification.** If your developer account has not finished ID verification, nothing publishes. Check *Setup → Developer account* first, it can take days.
- **The 12-testers-for-14-days rule** applies to personal accounts opened after November 2023 and gates **production** only. Internal testing is unaffected.
- **Screenshots contain real album art** from the test device's library. This is genuine app content and normal for a music player, but if you would rather not, say so and I will regenerate them against a library of public-domain covers.
- **`dev.crossfeed`** (the non-player app) is a separate Play listing if you ever want it there. Same key, same process, different package name.
