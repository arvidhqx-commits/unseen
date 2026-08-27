# Unseen

**Staff vanish that survives relogs and restarts. For Paper 1.21+ and 26.x.**

---

## Why this plugin exists

The most-downloaded free vanish plugin on Modrinth (176k downloads) has been unmaintained since August 2023,
and the next one down has only 9k downloads. Servers need vanish on day one of every Minecraft update — so
this is a plugin worth keeping current.

## Features

- **`/vanish`** — invisible to everyone without `unseen.see`
- **Fake leave and join broadcasts**, so nobody notices the staff member disappearing
- **Silent real joins and quits** while vanished
- **No item pickup, no mob targeting, invulnerable** while vanished
- **Action-bar reminder** so you never forget you are still hidden
- **State persists** across relogs and server restarts
- No dependencies, one small jar

## Commands

| Command | What it does |
|---|---|
| `/vanish` (aliases `/v`, `/unseen`) | Toggle vanish |

| Permission | Default | Meaning |
|---|---|---|
| `unseen.use` | op | May vanish |
| `unseen.see` | op | Sees vanished players |

## Compatibility

Built for the Paper API 1.21 and up. Every release is started on a **live Paper 1.21.11 server and a live
Paper 26.2 server** and the actual behaviour is checked — not just "the plugin loads".

## Updates

Fast updates on new Minecraft versions are the point of this project.

## Source & licence

MIT licensed, source on [GitHub](https://github.com/arvidhqx-commits/unseen).

## Development note

This project is **AI-assisted**: the code is written with Claude under the direction, testing and release
approval of the maintainer. Every release is run against a live Paper server before it ships.
