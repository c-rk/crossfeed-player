# Repo notes & open questions

Source repo: **c-rk/crossfeed-player**, branch `main`. The web app the design was cross-checked against is https://crossfeed.tentkotta.org/.

## Before writing any UI

1. Confirm the UI stack. The `code/` samples are written for **Jetpack Compose** with a Kotlin package of `org.tentkotta.crossfeed.*`. If the repo is Views/XML or uses different package names, keep the *values* and discard the *scaffolding* — the tokens and geometry are the deliverable, not the class layout.
2. Find the existing theme/colour source and decide whether to extend it or introduce `GlassColors` alongside it. Do **not** end up with two competing colour systems.
3. Find the existing routing/link-resolution code. The design's ranked multi-route model probably replaces a single stored preference — check what is persisted today and write a migration (a single stored service becomes a one-item list).
4. Check the DataStore/SharedPreferences layer for where to persist `routes`, `mode`, `listView`, `auxView`.

## Order of work (suggested)

1. `Theme.kt` + `GlassCard.kt` — nothing else can be built correctly until the tokens and the panel exist.
2. **Settings**, starting with the routing dial. It is the highest-risk piece and it drives the accent for every other screen, so getting it first makes the rest verifiable.
3. **Listening** — the stat card, then the list/grid toggle.
4. **Auxshare** — the tray and the reaction picker, both of which are just "expand a GlassCard in place with `rise`".
5. **Player**.
6. **The sheet** — including moving sing-along off the player card, which is the change the user most wants to see.

## Things that are decisions, not details

Do not quietly change these; they are the point of the redesign.

- **The accent comes from the primary route.** If you hard-code an accent, the design loses its central idea.
- **Sage is reserved for other people.** Friends, reactions, replies, sharing. Never accent those, never sage anything else.
- **No emoji reaction drawer.** Exactly four reactions, fixed order, thumb-sized.
- **Notifications never open a screen or a dialog.** The tray expands in place.
- **Sing-along is a tab in the sheet.** Not a button on the player card.
- **Small text stays at `t3`.** The design was explicitly revised for legibility; do not restore lower-opacity greys.

## Open questions for the user

1. **Accent behaviour:** currently the accent follows the *primary route* (a setting). The alternative is following the *currently playing* track's service, which changes as the user moves between apps. The prototype implements the former.
2. **Nav model:** the prototype uses a four-dot swipe pill with no labels. Users unfamiliar with the app may need labels at first run — worth an onboarding hint or a labelled variant.
3. **Dial on small screens:** 250dp plus padding is comfortable on a 360dp-wide screen, but check 320dp devices; the fallback is shrinking the orbit to 78dp and the nodes to 46dp.
4. **Deezer/Tidal link hosts** in `Routing.kt` are best guesses. Verify against real share links before shipping.
5. The settings sub-cards from the reference screenshot (try a link, behaviour, recent, link handling) keep their existing copy and flows — confirm nothing there has changed since the screenshot.

## Screen map (design → repo)

Fill this in as you go; it makes the next sync cheap.

| Design screen | Repo file(s) |
| --- | --- |
| listening | |
| auxshare | |
| player | |
| settings / routing | |
| now-playing sheet | |
