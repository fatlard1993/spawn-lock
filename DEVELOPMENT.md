# Spawn Lock - Development Guide

For what the mod is and how it plays, see [README.md](README.md).

## Source Map

| File | What is in it |
|---|---|
| `Main.java` | The events that hold a player at the door, and the commands: `/login`, `/spawnlock`, `/jail`, `/unjail` |
| `Gate.java` | Who is held, why and for how long: the pen, the wall, the countdown, the way home |
| `SpawnLockConfig.java` | The password, the memory of who said it, the jails, and the file they live in |
| `MenuPage.java` | The allowed list on the Pandorical mods menu, operators only |
| `mixin/ChatCommandMixin.java` | At the door the only command is `/login` |
| `generate_icon.py` | The mod menu icon, cut from vanilla's own textures |

## Building

`./gradlew build`; the jar lands in `build/libs/`. The suite's `mc-build spawn-lock` does the
same and reports one line. Pandorical is compiled against as a sibling project
(`../pandorical`), the way every suite mod that touches it does.

## Trying it

Set a password with `/spawnlock set <word>` as an operator, log out and back in, and you are
at the door. `/spawnlock forget` empties the memory so you can go through it again;
`/jail <you> 1` is a one-minute look at the jail from inside.
