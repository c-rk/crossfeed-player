# Handoff: Crossfeed — "Glass" mobile redesign

## Overview

A full UI/UX redesign of the Crossfeed Android app across its four pages — **listening** (the diary), **auxshare** (the friends feed), **player** (local library), **settings** — plus the **now-playing sheet** that carries sing-along.

The design direction is called **Glass**: rounded, warm shapes on a near-black (or near-white) ground, with translucent blurred panels, a soft accent bloom behind each screen, and **the accent colour inherited from the music service the user routes through**. Routing supports **several services at once, ranked**.

Three problems from the current app were explicitly targeted:

1. **Notifications** — no bell, no modal. A counted pill expands an inline "tray" card in place.
2. **Reactions** — replaced the generic emoji row with four fixed, thumb-sized reactions (`a banger / on it now / keeping it / reply`), shown back as overlapping bubbles with names.
3. **Sing-along** — no longer a stray button on the player card. It is a segmented tab in the sheet plus a lyric strip on the listening page.

## About the design files

The files in `reference/` are **design references authored in HTML** — a prototype showing intended look and behaviour. **They are not production code to copy.** Crossfeed is an Android app, so the task is to **recreate these designs in the app's existing environment** (Jetpack Compose / Kotlin, or whatever the repo already uses — see `repo-notes.md`) using its established patterns, theming, and navigation.

`code/` contains **real, ready-to-adapt Kotlin/Compose source** for the parts that are fiddly to derive from a screenshot: the theme token objects for both colour modes, the six service accents, the routing model, and the radial routing dial. Treat those as a strong starting point, not gospel — match the repo's package names, DI, and state conventions.

## Fidelity

**High-fidelity.** Colours, type sizes, radii, spacing, and copy are all final and exact. Recreate pixel-close, but substitute the repo's own component primitives where they exist. Two deliberate exceptions:

- **Album art** is represented by lettered gradient squares in the prototype. In the app, use real embedded artwork with those gradients as the fallback.
- **Service logos** on the routing dial are text glyphs in the prototype. In the app, use the real brand marks (see *Assets*).

## Design tokens

### Colour modes

Every colour is a token, so both modes come from one set. Prototype values, verbatim:

| Token | Dark | Light | Role |
| --- | --- | --- | --- |
| `bg` | `#0B0B0C` | `#F4F2EE` | screen ground |
| `t1` | `#F7F5F2` | `#141416` | primary text |
| `t2` | `rgba(247,245,242,.88)` | `rgba(20,20,22,.82)` | secondary text |
| `t3` | `rgba(247,245,242,.76)` | `rgba(20,20,22,.66)` | meta/small text |
| `line` | `rgba(247,245,242,.30)` | `rgba(20,20,22,.20)` | track/progress rails |
| `dot` | `rgba(247,245,242,.50)` | `rgba(20,20,22,.34)` | inactive nav dots |
| `p0` | `rgba(255,255,255,.05)` | `rgba(255,255,255,.50)` | faintest panel |
| `p1` | `rgba(255,255,255,.07)` | `rgba(255,255,255,.66)` | standard glass panel |
| `p2` | `rgba(255,255,255,.09)` | `rgba(255,255,255,.80)` | raised glass panel |
| `p3` | `#1C1C1F` | `#E2DDD5` | opaque chip/avatar fill |
| `bd` | `rgba(255,255,255,.14)` | `rgba(20,20,22,.14)` | 1px panel border |
| `hi` | `rgba(255,255,255,.14)` | `rgba(255,255,255,.90)` | inset top highlight |
| `off` | `rgba(255,255,255,.22)` | `rgba(20,20,22,.20)` | switch track, off |
| `scrim` | `rgba(11,11,12,.60)` | `rgba(244,242,238,.78)` | badge over artwork |
| `sage` | `#AEBF92` | `#59703C` | **other people** |
| `sageTint` | `rgba(174,191,146,.18)` | `rgba(122,138,94,.20)` | sage panel fill |
| `sageBorder` | `rgba(174,191,146,.28)` | `rgba(122,138,94,.34)` | sage panel border |

**Legibility rule (important):** the meta tier is `t3`, which is ~76% opacity in dark and ~66% in light. Do **not** drop small text below this — an earlier iteration used .45 and it was illegible. Minimum font size anywhere is 9px for all-caps tracked labels, 9.5px for sentence-case meta.

### Service accents

The accent is **not** a brand constant — it is inherited from the primary route.

| Service | Accent | RGB (for blooms/rings) |
| --- | --- | --- |
| apple music | `#FF375F` | `255,55,95` |
| spotify | `#1ED760` | `30,215,96` |
| yt music | `#FF5A3C` | `255,90,60` |
| deezer | `#A45CFF` | `164,92,255` |
| tidal | `#3AD0FF` | `58,208,255` |
| on device | `#F6A06B` | `246,160,107` |

Accent bloom alpha: **.34 in dark, .20 in light**.

`sage` is deliberately fixed across all services — it always means "other people" (friends listening, reactions from others, replies, sharing). Never tint sage with the accent.

### Type

- **Display / headings:** Caprasimo (regular 400 only), letter-spacing `-0.015em`. Sizes used: 25px (page titles), 22–24px (figures in stat cards), 15–18px (section headings), 54px (hero figure, letter-spacing `-0.03em`, tabular).
- **UI / body:** Figtree. 700 for titles and labels, 600 for chips/timestamps, 400 for descriptions. Sizes 9 → 14px.
- **Lyrics only:** Lora — 25px/1.28 for the active line, 17px/1.35 for neighbours, 13px italic for the translation. This is the one serif in the app and it appears **nowhere else**.
- Figures (durations, counts, sizes) use **tabular numerals**.

### Geometry

| Value | Use |
| --- | --- |
| 30px | phone screen corner |
| 22px | standard glass card |
| 20px | feed grid card |
| 16px | album art in cards |
| 14px | small art / wheel tiles |
| 12–13px | list-row art |
| 999px | every pill, chip, switch, nav, transport button |

Panel recipe, used everywhere: `background p1` + `1px border bd` + `inset 0 1px 0 hi` + `blur(22px)`. **No drop shadows** except on the routing dial's selected nodes and the wheel's centre puck.

Spacing: screen padding 18–22px, card padding 12–15px, gaps 6–9px between cards, 8–11px inside lists.

### Motion

One shared entrance for anything that expands in place (tray, reaction picker):

```
name: rise
from: opacity 0, translateY(6px)
to:   opacity 1, translateY(0)
duration: 180–220ms, ease
```

No other animation is specified. Do not add spring/bounce.

## Screens

Each screen is 360 × 740 dp in the prototype. See `screens.md` for the full component-by-component spec of all five, and `screenshots/` for renders.

| Screen | File section | Purpose |
| --- | --- | --- |
| listening | `2a listening` | the diary: hero figure, stat block, now-playing, lyric strip, today's plays (list **or** grid) |
| auxshare | `2a auxshare` | friends: notification tray, who's listening, the feed (list **or** grid), reaction picker |
| player | `2a player` | local library: glass search, "back on the turntable" records row, album grid |
| settings | `2a settings` | routing dial, permissions, sharing, export |
| sheet | `2a sheet` | now playing: `now / sing along / queue` tabs, lyrics, transport |

## Interactions & behaviour

- **Bottom nav** is a floating four-dot pill, not tab bar labels. The active page is a 26×7 rounded bar in the accent; others are 7px dots at `dot`. Pages are swipeable left/right in that order: listening, auxshare, player, settings.
- **Period chips** (today / week / month / all time) filter the listening figures. Values in `screens.md`.
- **Grid/list toggle** on both the diary and the auxshare feed. Persist per feed, independently. Default **list**.
- **Notification tray**: the counted circle expands a card in place with `rise`. It is not a screen, not a dialog. Rows carry an inline play button when the notification is a song reply.
- **Reaction picker**: tapping/long-pressing a feed item expands the four-reaction card in place with `rise`. Always the same four, always in the same order.
- **Routing dial**: tap a logo to add or remove it from the route list (never allow zero). Tap a rank chip to promote that service to first. The first route drives the app accent everywhere. With one route, a link opens straight through; with several, show a chooser sheet ordered by rank.
- **Light/dark**: user-selectable, and should also honour system default on first run.

## State

```
routes: List<Service>      // ordered, never empty; routes[0] drives the accent
mode: dark | light
listView: list | grid      // diary feed
auxView: list | grid       // auxshare feed
period: today | week | month | all
trayOpen: Boolean
reactionTargetId: String?  // which feed row has the picker open
```

All of it is UI state and should survive rotation; `routes`, `mode`, and both view toggles persist across launches.

## Assets

- **Service logos** — use each service's official brand mark, from their own brand/press resources. Do not recolour them; the dial fills the node with the brand accent and the mark sits on top. The prototype's text glyphs (`♫ ◍ ▶ ▮▮▮ ◆ ⌂`) are placeholders only.
- **Album art** — real embedded artwork. Fallback: a 150° linear gradient from the palettes in `screens.md` with the first letter of the title in Caprasimo at 45% `t1`.
- **Icons** — Lucide, at stroke-width 2.75 (rounder/heavier, matching the system's icon guidance). The prototype uses unicode glyphs as stand-ins for transport, search, and grid/list.
- **Fonts** — Caprasimo and Figtree and Lora, all on Google Fonts. Bundle them; do not fetch at runtime.

## Files in this bundle

```
README.md                            this file
screens.md                           per-screen, per-component spec (start here when building a screen)
repo-notes.md                        what to look at in c-rk/crossfeed-player, and open questions
code/Theme.kt                        colour tokens for both modes + service accents, as Compose
code/Routing.kt                      the multi-route model and its rules
code/RoutingDial.kt                  the radial dial, drawn with Compose Canvas + layout
code/GlassCard.kt                    the panel recipe as a reusable composable
reference/2a-glass-screens.html.txt  the prototype markup for all five screens (design reference)
reference/logic-class.js.txt         the prototype's state logic (design reference)
screenshots/board.png                all five screens, dark
screenshots/01-state.png             light mode
screenshots/02-state.png             grid mode on both feeds
```

Start with `screens.md`. Use `code/` to skip the tedious parts. Use `reference/` only to check a value you cannot find in the docs.
