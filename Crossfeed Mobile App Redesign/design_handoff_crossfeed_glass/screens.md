# Screens — component-by-component

All screens are 360 × 740 dp. Screen padding is 18px horizontal unless noted (player and settings use 22px for their headers). Every "glass card" means: `background p1`, `1px border bd`, `inset 0 1px 0 hi`, `blur(22px)`, radius 22px.

Every screen has, in this order, bottom-up: the **mini player** (a 999px glass pill, 8px/10px padding, 34px round art, title 12px/700, sub 9.5px/400 `t3`, accent play circle 32px, secondary skip circle 32px at `p2`) and the **four-dot nav pill**.

Behind every screen sit one or two accent blooms — radial gradients, `closest-side`, no border:

- listening / player / settings: one at top, inset `-40% -30% auto -30%`, height 420px, colour = accent at bloom alpha.
- listening also: one bottom-left, 340px, sage at .22.
- auxshare: top bloom is **sage** at .30; bottom-right 320px bloom is the accent.
- sheet: one top bloom, height 480px, accent.

---

## 1. Listening (the diary)

**Header row** — title "listening" (Caprasimo 25px) left; right side has a service pill (30px tall, 999px, `p2` bg, `bd` border, blur 14px, a 6px accent dot + the service name at 10px/700) and a 30px round search button.

**Period chips** — 4 pills, 7px/13px padding, 11px/600. Selected: accent bg, `#0B0B0C` text, accent border. Unselected: `p0` bg, `t3` text, `bd` border.

Values by period:

| Period | Hero (hours) | Plays | Tracks | Artists | Caption |
| --- | --- | --- | --- | --- | --- |
| today | 2:10 | 41 | 33 | 27 | today |
| week | 27:20 | 609 | 365 | 298 | this week |
| month | 96:05 | 2 140 | 1 180 | 731 | this month |
| all time | 412:44 | 9 840 | 4 210 | 1 866 | all time |

**Stat card** (glass, padding 16/17/14). Left: hero figure Caprasimo 54px, line-height .9, tracking `-0.03em`, tabular; under it a 9.5px/700 all-caps accent label tracked `.16em` reading `hours · {caption}`. Right: a 7-bar week sparkline, 104×44px, 3px gaps, 3px radius, bars at `line`-ish `rgba(247,245,242,.18)` with the peak bar in the accent (heights 38/56/30/78/100/64/44%).
Below, separated by a 1px `bd` rule with 12px padding: three columns — figure Caprasimo 22px tabular, label 8.5px/700 all-caps tracked `.14em` at `t3`. Columns 2 and 3 have a 1px left border and 13px left padding.

**Now-playing card** (glass, padding 12/13) — 46px art at radius 15px, title 13.5px/700, sub 10.5px/400 `t3`, a 3px progress rail (`line` track, accent fill at 34%), and a 38px accent play circle.

**Lyric strip** — radius 18px, `sageTint` bg, `sageBorder` border, padding 11/14. Lora italic 13px/1.45 at `t1` for the lyric, and `sing along` at 9px/700 all-caps tracked `.12em` in `sage`, right-aligned, nowrap.

**Section header** — "the day so far" (Caprasimo 16px) + a grid/list segmented toggle: 999px, 3px padding, `p1` bg, `bd` border; each option 26×21px, radius 999px, 10.5px glyph. Active: accent bg, `#0B0B0C` glyph. Inactive: transparent, `t3`.

**List view** (default) — rows of 7px/5px padding, 1px `p2` top border between them, 10px gaps: 30px art at radius 9px; title 12px/700 with the artist appended in 400 at `t3`; a tabular 10px/600 `t3` timestamp; then a 32px right-aligned completion figure at 9.5px/700 — `sage` when the track was finished or repeated, `t3` otherwise. Four rows: Hum · murtaza qizilbash · 14:22 · 100%; Kannukkul Kannai · a. r. rahman · 13:58 · 41%; Co2 · prateek kuhad · 07:12 · ×3; Priyotama · anupam roy · 06:40 · 88%.

**Grid view** — 3 columns, 11px/9px gaps. Square art at radius 14px with the letter in Caprasimo 19px at 50% `t1`, and the completion badge bottom-right (radius 999px, `scrim` bg, 4px/6px padding, 8.5px/700). Title 10.5px/700 truncated, timestamp 9px/400 `t3`.

Art gradients (150°, used throughout): Hum `#5C2A30 → #2A1418`; Kannukkul `#3D472B → #1C2015`; Co2 `#4A2A12 → #1F1208`; Priyotama / Nothing Else Matters `#2F3238 → #15171A`; Redbone `#E8E2D6 → #9A9488` (letter at 40% of `#0B0B0C`).

---

## 2. Auxshare

**Header** — "the aux" (Caprasimo 25px) with "@rc · four people, no audience" at 10.5px/400 `t3` under it. Right: a 36px **sage** circle with the unread count in Caprasimo 16px at `#141410`. Tapping it toggles the tray.

**Tray** (glass card, padding 14/15, `rise` in) — heading "the tray" Caprasimo 16px, and a `clear` action at 9.5px/700 all-caps tracked `.12em` `t3`. Three rows separated by 1px `p2` rules, 11px vertical padding: 34px round avatar (gradient + initial in Caprasimo 13px), then 12px/600 copy with the song bolded, then a 9.5px `t3` timestamp. The `@pranav answered with Redbone` row carries a 30px accent play circle on the right.

Copy: `@soumadeep called Priyotama a banger · 2h` / `@pranav answered with Redbone · 5h` / `@rahul played 14 of your saves · yesterday`.

**Who's listening** — three 70px columns. Each: a 60px conic-gradient ring (`sage` for the played fraction, `rgba(247,245,242,.13)` for the rest) around a 51px `p3` disc holding the initial in Caprasimo 18px. Fractions: @rahul 72%, @pranav 34%. Third column is an "add" affordance — 1.5px dashed `dot` circle, 50% opacity. Handle 10.5px/700, subtitle 9px/400 `t3` truncated.

**Section header** — "the feed" (Caprasimo 16px) + "8 today" (10px/600 `t3`) on the left, grid/list toggle on the right (identical to the diary's).

**List view** (default) — rows 8px/5px padding, 1px `p2` rules, 11px gaps: 40px art at radius 12px; title 12px/700; sub 9.5px/400 `t3` reading `artist · @handle · age`; then the reaction cluster — 20px round bubbles at `p3` with a 1.5px `bg`-coloured border, overlapping by `-7px`, plus a 9px/700 `t3` count. The Redbone row instead shows `↩ answered` in `sage` 9px/700.

**Grid view** — 2 columns, 10px gap. Each is a glass card at radius 20px, padding 10px: square art at radius 13px with the Caprasimo 22px letter, title 11px/700, `@handle · age` at 9.5px/400 `t3`, then the same bubble cluster at 7px top margin.

**Reaction picker** (glass card, `rise` in, appears under the feed) — "hold to react" Caprasimo 15px, then four 48px circles spread evenly with 9px/700 `t3` labels beneath: 🔥 **a banger** (accent fill), 🎧 **on it now** (`p2` + `bd`), 💾 **keeping it** (`p2` + `bd`), ↩ **reply** (**sage** fill, `#141410` glyph). Footnote at 10.5px/1.55 `t3`: "four, thumb-sized, always the same four. the reply sends a song back instead of a face."

The reply action is sage, not accent, because it produces something from another person. Keep that.

---

## 3. Player (the shelf)

**Header** — "your shelf" (Caprasimo 25px) with "307 albums · offline · yours" at 10.5px/400 `t3` tabular. Right: a 30px round grid-toggle button.

**Search** — a 999px glass pill, padding 12/16, accent search glyph + "search here, or all of music" at 12px/400 `t3`. This is a **local + remote** search: local results first, then "all of music" via the routed services.

**Scope chips** — songs / albums / artists / folders. Selected (albums): accent bg, `#0B0B0C`. Others: `p1` bg, `bd` border, `t3`.

**"back on the turntable"** (Caprasimo 14px) — a horizontally scrolling row of 96px **circular** records: a conic gradient from the album's palette with a 26px `bg` centre hole ringed 1px `bd`. Title 11.5px/700, "N tracks" 9.5px/400 `t3`. Three items: Brave (9), 96 (Theme) (1), Cold/Mess (4).

**"everything, a — z"** (Caprasimo 14px) with a "newest first ⌄" sort control at 9.5px/600 `t3`. Then a 3-column grid, 13px/11px gaps: square art at radius 16px with the Caprasimo 21px letter, title 11px/700 truncated, "N tracks" 9px/400 `t3`. Nine tiles shown.

---

## 4. Settings

**Header** — "your call" (Caprasimo 25px) and "turn on only what buys you something." at 11px/1.5 `t3`.

### Routing dial card (glass, padding 12/13/13)

Header row: `WHERE MUSIC COMES FROM` at 9px/700 all-caps tracked `.18em` in the accent, and a dark/light segmented toggle on the right (999px, 3px padding, options 5px/9px at 9px/700; active = `t1` bg with `bg`-coloured text).

The dial itself: a **250px circle**, centred. Background is a `closest-side` radial gradient from `p2` to `p0`, with a 1px `bd` border and `inset 0 2px 14px rgba(0,0,0,.32)`.

- **Ticks**: 12 marks, 1px wide, at the top, `transform-origin: 50% 119px`, rotated in 30° steps. Every third is 9px tall, the rest 5px. Colour `line` at 55% opacity.
- **Hub**: a 118px centred disc — `p2` bg, 1px `bd`, `inset 0 1px 0 hi`. Inside, the primary service name at 13px/700 `t1`, and beneath it a summary at 9.5px/400 `t3`: "the only route" when one, otherwise "+ N more, in order".
- **Nodes**: six 52px circles on a **radius of 88px** from the centre, first at 12 o'clock, stepping clockwise by 60°: apple music, spotify, yt music, deezer, tidal, on device. Selected nodes fill with **their own brand accent**, get `0 0 0 3px rgba(serviceRgb,.28)` and `0 4px 14px rgba(0,0,0,.35)`, and carry a 17px rank badge at the top-right (`bg` fill, brand-coloured numeral, 9px/700). Unselected nodes are `p1` with a 1px `bd` border and a `t3` glyph.

Below the dial: the **rank chips**, one per selected route in order, labelled `N · service name`. The first is accent-filled; the rest are `p1` + `bd` + `t3`. Then the instruction line at 10px/1.5 `t3`: "tap a logo to add or drop it · tap a chip to make it first. one is a shortcut, several is a chooser sheet."

**Rules:** tapping a logo adds it to the end of the route list, or removes it — but never below one. Tapping a rank chip promotes that service to first, which immediately retints the whole app.

Per the reference screenshot, the shipped version should also carry, beneath the dial card: a **try a link** card (paste field + resolve), a **behaviour** card (`prefer my own files`, `open straight away`), a **recent** list of resolutions showing `source → destination`, and the **link handling** card explaining Android's verified-owner behaviour with per-app "fix" shortcuts. Those are existing app features and keep their current copy — restyle them into the glass card recipe; don't redesign the flows.

### Permissions card (glass, padding 4/14)

Three rows, 11px padding, 1px `p2` rules, each with a title at 12.5px/700 and a description at 10px/1.45 `t3`, and a 40×23px switch (999px; on = accent, knob 17px `bg`-coloured, right; off = `off` track, knob `t3`, left).

- **your music files** — "so the shelf isn't empty" — on
- **live control** — "scrubber, timed lyrics, and pausing {service} from in here" — on
- **browser capture** — "a tab must prove it's music first" — off

### Sharing card (`sageTint` bg, `sageBorder` border, padding 4/14)

- **share what i play** — "the track and how long — never the diary" — on, switch is **sage**
- **find me nearby** — "a distance band, ~110 m · pause sharing for an hour" — off

### Export card (glass, padding 14)

"870 rows on disk" at 12px/700 with "eleven sheets, to downloads" at 10px/400 `t3`; on the right, "14.9" in Caprasimo 24px tabular with a 11px `t3` "mb". Two 999px buttons: **export** (accent fill, `#0B0B0C` text) and **import** (`p2` + `bd`, `t2` text), both 12px vertical padding, 11px/700.

---

## 5. The sheet (now playing)

Presented as a bottom sheet: a 42×4px `dot` handle, then 22px padding.

**Tabs** — a 999px glass container with 4px padding holding three options: `now`, `sing along` (flex 1.2, active), `queue`. Active: accent bg, `#0B0B0C`. Inactive: `t3`. **Sing-along is a tab here — never a floating button on the player card.** That was the specific complaint this solves.

**Track card** (glass, padding 12/13) — 50px art at radius 16px, title 14px/700, "a. r. rahman · naresh iyer" at 10px/400 `t3`. On the right, stacked: a language label `தமிழ் → en` at 8.5px/700 all-caps tracked `.1em` `t3`, and a 36×20px **sage** switch for the translation.

**Lyrics** — Lora, 18px gaps between lines. The active line is 25px/1.28 at `t1` with its translation beneath in 13px/1.45 italic **in the accent**. Neighbouring lines are 17px/1.35 with translations at 11.5px `t2`, at 30% (previous) and 40% (next) opacity; the line after that is at 20%. Sample content (Kannukkul Kannai): "கண்ணுக்குள் கண்ணை / the eye inside the eye" → **"நெஞ்சுக்குள் நெஞ்சை / the heart inside the heart"** → "உள்ளத்தில் உள்ளம் / the soul within the soul" → "என்னைக் கேட்காமல்".

**Transport card** (glass at radius 24px, padding 14/16/16) — a 4px progress rail (`line` track, accent fill at 42%, a 12px accent knob), times at 10px/600 tabular `t3` (2:23 / 5:42), then centred transport with 24px gaps: 44px `p2` back, **62px accent** play/pause, 44px `p2` forward. Under it, two 999px buttons at 10px/700: `⇗ links` (`p2`) and `↗ to the aux` (`sageTint` bg, `sage` text).
