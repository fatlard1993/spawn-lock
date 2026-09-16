# Spawn Lock

A Minecraft Fabric mod. A pen at the spawn, for two kinds of people: the ones who have not said
the password yet, and the ones an operator has put there.

## What This Mod Does

**The pen.** Whoever is held is taken to the world spawn and kept within sixteen blocks of it
(`penRadius` in the config), which is vanilla's own spawn protection: ground nobody but an
operator can change. The edge is drawn as a world-border wall that only they can see and cannot
walk through. Inside it they cannot break, place, use or hit anything, and nothing can hurt them:
every kind of damage is refused, fire is put out, and air does not run out. Let out, they go back
exactly where they were taken from, dimension and all. That spot is kept in the config file, so
somebody who leaves mid-hold is put back on their next visit rather than left at the spawn.

**A password at the door.** For a server on a public address that runs in offline mode, where a
name is anything a player types and a whitelist is a list of names. A newcomer is held at the
spawn, unseen, and asked for the password big on the screen, in the action bar with a countdown,
and in chat. They say it in chat, or as `/login <password>`, so a stock client with no mods can
say it, and what is said at the door never reaches anyone else. They have a minute and a half and
three tries; then they are back at the server list. A name together with the address it came from
is remembered after that and walks straight in, for good by default; `clearEveryDays` in the
config wipes the memory on a schedule so the word is asked again. A game with Pandorical 1.3.9 or
later is also handed a pass when it is let in, and the pass lets that name back in from any
address, so a household whose provider hands it a new address every few days is not asked again
each time. A game without Pandorical is known by its address alone.

**A jail.** An operator's `/jail <player>` puts them in the pen until `/unjail <player>` lets them
out; `/jail <player> <minutes>` lets them out on its own after that long. A jailed player is
visible, can talk, and can do nothing else, commands included. A sentence outlives a relog: they
come back into the pen.

**Letting somebody in by name.** `/spawnlock allow <player>` as an operator admits that name from
anywhere, no password asked; `/spawnlock disallow <player>` takes it back, pass and all. The
Spawn Lock page of the Pandorical mods menu shows every name that may walk in, with where it is
known from and a button to forget it. Operators only.

## Setting the password

`/spawnlock set <password>` as an operator, or edit `config/spawn-lock.json` and restart. Setting
it forgets everyone, passes and all, so the new word is asked of all. `/spawnlock forget` asks
everyone again without changing it. `/spawnlock off` removes it, and the server says so in its
log at every start until one is set again: an open door is never quiet. The jail works with or without a password.

The same file holds the timeout (`timeoutSeconds`, 90), the number of tries (`attempts`, 3), the
pen radius (`penRadius`, 16), how often the memory is wiped (`clearEveryDays`, 0 for never), the
current jails, the passes, and the memory itself, so a name can be let in ahead of time by writing
it there with the address it will come from. Edit it while the server runs and say
`/spawnlock reload`, or the next save writes over your edit.

## Pandorical

Required on the server, 1.3.9 or later. Not needed on the client: the door and the jail are
chat, a border packet and a teleport, and a stock client sees all of it. A client with
Pandorical 1.3.9 or later carries the pass described above; without it the address rule stands.
Operators get the allowed list on the mod's page of the mods menu, with a button to forget each
name.

## Development

Installing and the map of the source are in [DEVELOPMENT.md](DEVELOPMENT.md).

## License

MIT, see [LICENSE](LICENSE).
