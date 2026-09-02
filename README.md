# MCHelden

A game mode for **Minecraft Heroes 1** — a limited-run, twenty-player survival event built
around a single scarce resource: hearts.

**Minecraft 1.21.1 · NeoForge 21.1.248 · Java 21**

The mod builds game systems, plus four deliberate loot and spawn rules. It does not touch world
generation and does not block items. What data it does ship *modifies* vanilla tables rather
than replacing them, so it sits alongside History Stages' data pack instead of fighting it.

It has to be installed on **both sides**. The rules live on the server, but the HUDs, the
grave screen and the renderers for the wall and the safe zone are client code.

---

## Contents

- [The short version](#the-short-version)
- [Game systems](#game-systems)
  - [Hearts](#hearts)
  - [The combat timer](#the-combat-timer)
  - [Graves](#graves)
  - [Bounty](#bounty)
  - [Phases](#phases)
  - [The dividing wall](#the-dividing-wall)
  - [The safe zone](#the-safe-zone)
  - [Daily playtime](#daily-playtime)
  - [Starting spawn](#starting-spawn)
  - [The world border and the Final War](#the-world-border-and-the-final-war)
  - [The arena centre](#the-arena-centre)
  - [Loot and spawns](#loot-and-spawns)
- [Command reference](#command-reference)
  - [Players and hearts](#players-and-hearts)
  - [Combat](#combat)
  - [Bounty commands](#bounty-commands)
  - [Phase control](#phase-control)
  - [Wall](#wall)
  - [Final War and border](#final-war-and-border)
  - [Arena centre](#arena-centre)
  - [Playtime](#playtime)
  - [Reset](#reset)
  - [Debug](#debug)
  - [Duration format](#duration-format)
- [Development](#development)
- [License](#license)

---

## The short version

Everyone starts with three hearts. Losing one costs a life; running out ends your run. A
fourth heart exists, but there is exactly one way to get it: kill the one player the bounty
roll paired you with — who is hunting you at the same time.

Death is defined by the combat timer, not by the damage source. Get hit by a player and you
are "in combat" for at least thirty seconds, and for up to three minutes in a long fight. Die during that window — to the player, to a fall
while fleeing, to lava, to anything — and it counts as a player kill. Log out during that
window and it counts as a death too.

Everything you carry is split in half on death. Your half comes back with you; the other half
stays in a grave that anyone can open.

---

## Game systems

### Hearts

| | |
|---|---|
| Starting hearts | 3 |
| Maximum | 4 |
| Fourth heart | only via a bounty kill |
| At zero | eliminated |

A heart is lost when you die **with the combat timer running**. Dying outside combat costs no
heart — but it still creates a grave.

Elimination is not a vanilla ban. The ban list stays free for actual rule breaking, and a
wrongly eliminated player is one command away from being back rather than a hand edit in a
JSON file. On a server an eliminated player is kicked; in a single-player world they are put
into spectator mode instead, since you cannot lock someone out of their own world.

### The combat timer

The hinge the whole rule set hangs off — heart loss, the GUI lock, the item quota and safe
zone entry all read it.

| | |
|---|---|
| First hit of a series | starts the timer at 30 seconds |
| Every fifth hit after that | +30 seconds |
| Cap | 180 seconds — reached at the 25th hit |
| A series ends | after 30 seconds without a hit; the next hit starts a new one |
| Counted | per player, across all opponents at once |
| Applies to | both players — attacker and target |
| Projectiles | count as hits; an arrow from a hundred blocks away is the same as a sword swing |

Not every hit extends the timer, on purpose. When each one did, six swings pinned both players
at the three-minute cap — an ordinary fight maxed it out immediately. Only hits that actually
land are counted: inside the safe zone damage is cancelled, so swinging there starts nothing.

While the timer runs:

- **Death counts as a player kill**, whatever actually killed you. This is deliberate — you
  are not supposed to be able to throw yourself out of a fight.
- **Logging out counts as a death.** Without this the timer is worthless, because every lost
  fight would end in Alt+F4.
- **Every screen except your own inventory is locked.** Chests, ender chests, shulkers,
  furnaces, crafting tables, anvils, brewing stands, chest-carrying donkeys and llamas, chest
  boats, chest minecarts. The check asks whether a block would open a screen at all, so the
  rule cannot forget anything — not even blocks Mojang adds later. **Graves are exempt:** the
  timer keeps running for up to three minutes after a kill, and a player who is not allowed
  to touch their own loot while any bystander can is being robbed of the win.
- **Ender pearls and cobwebs are rationed** — see below.
- **The safe zone will not let you in.** Without that rule it would be an escape button.

The timer is deliberately not persisted. A logout with the timer running counts as a death,
so there can be no saved combat state to restore on restart.

#### Item quota

Applies only while the combat timer is running, and resets with it. Outside of combat, use as
much as you like.

| Item | Limit per fight |
|---|---|
| Ender pearls | 32 (two stacks) |
| Cobwebs | 128 (two stacks; placing counts as use) |

Golden apples are deliberately not on the list: without the Nether every ingot comes from
ordinary mining, and eight ingots per apple already limits them through the world itself.

### Graves

A grave is created on **every** death, not just player kills. Heart loss hangs off the combat
timer, the grave hangs off the fact of dying — two different rules.

The estate is split 50/50 between what you keep and what stays behind:

- **Stacks** are halved. On an odd count the grave gets the extra piece — 33 becomes 16 for
  the player and 17 for the grave.
- **Armor** is dealt out two-to-two.
- **Non-stackable items** are distributed at random, half and half.
- **XP:** the player keeps nothing and respawns at zero. Half goes into the grave, the rest is
  gone.

The grave itself is a headstone with the owner's skin, a floating name plate showing the time
of death, and a short beam of light while it is fresh. It is indestructible in survival and
piston-proof, disappears on its own once empty, and has no item form because it is never
placed by hand — only by dying. In creative it can be broken so admins can clean up, and
everything falls out when it does.

**Anyone can open a grave.** That is the rule that turns every death into a race.

### Bounty

The only route to a fourth heart.

`/helden bounty roll` pairs every known player with another — **mutually**. If you are hunting
someone, they are hunting you. A one-sided pairing would be invisible from the outside and
would silently break resolution, so nothing else in the code touches the pairing directly.

Everyone who has ever been on the server and is not eliminated gets paired, including players
who are currently offline. Only pairing whoever happens to be online would be the simpler
rule, but with a one-hour daily limit it would regularly lock someone out of the fourth heart
permanently without anyone noticing. Offline players get the wheel replayed on their next
join.

The roll is an eleven-second wheel of fortune: heads fly past, slow down, look like they have
stopped, creep one more tile, and snap into place with a gong. The personal chat line only
arrives once the head has landed, so the result never spoils its own reveal. It happens
exactly once per run, and all twenty players see it at the same time.

When the pairing resolves — you kill your target — you gain a heart and **the victim loses
none**. Only the heart is protected; grave, item split and XP all run normally. Both sides of
the pairing are cleared.

### Phases

| Phase | ID | What changes |
|---|---|---|
| Buildup | `buildup` | Wall up, safe zone active, one-hour daily playtime limit |
| War | `war` | Wall falls, no time limit |
| Final War | `finalwar` | Safe zone shatters, world border starts shrinking, boss bar appears |

Forward transitions get a countdown and a staged announcement — it is the biggest moment of
the run. Going back gets a single chat line: a rollback is an operator correction, not an
event.

| Countdown | Seconds |
|---|---|
| Ordinary transition | 5 |
| To War (wall drop) | 12 |
| To Final War | 10 |

The wall breaks open *during* the War countdown, not at the end of it — the announcement,
the particle wave and the wall's disappearance are one event. The Final War countdown works
the same way: the storm rolls in and the dome glows itself up to breaking point while the
countdown runs.

The playtime limit is deliberately not a stored switch — it is a question asked of the current
phase. That makes the reverse direction work by itself (`phase set buildup` brings the limit
back) and makes a state where phase and limit disagree impossible.

### The dividing wall

An invisible wall through the arena centre — **X = 0** unless the arena has been moved.

Minecraft only supports *one* world border per dimension, and that one is taken by the world
edge. So the wall is rebuilt from scratch: a coordinate check on the server, a dedicated
renderer on the client. Height takes care of itself — a coordinate check has no notion of up
or down.

**Nothing crosses.** No players, no ender pearls, no fishing hooks, no arrows, no thrown
items. A rule with no exceptions is one nobody has to look up.

### The safe zone

A cylinder of radius 50 around the arena centre — a hundred blocks across, halved by the
dividing wall into fifty per side.

A cylinder and not a sphere, because you leave a sphere by building upward: put a platform
down in the middle and you would suddenly be attackable without anything having changed.
Height does not count, so the zone needs no vertical centre — only a radius.

It is not spawn protection; starting spawns are scattered across the world. The point is that
the dome sits on the wall, and with proximity voice chat you can meet there for five days and
talk while the other half of the world is unreachable. Deals, alliances, threats.

After the wall falls it stays the one place where you can face someone without a fight
starting immediately. It disappears with the Final War — otherwise it would end up as an
apartment you are immortal in.

**Anyone in combat is kept out.**

### Daily playtime

One hour per day, during the build-up phase only. The point is fairness — nobody should grind
twelve hours on day one while everyone else is still in leather.

| | |
|---|---|
| Daily allowance | 60 minutes |
| Reset | 04:00 server local time |
| Warnings at | 10 minutes, 5 minutes, 1 minute remaining |
| Grace when the timer runs out mid-fight | up to 180 seconds |

The reset is at four in the morning rather than midnight, so nobody plays half an hour at
23:30 and gets the rest handed to them thirty minutes later. The grace period is tied to the
combat timer's cap: a fight cannot last longer than three minutes, so the kick never waits
longer than that.

Operators are exempt. Since Minecraft hands the world owner permission level 4 whenever cheats
are on, `/helden debug playtime` exists to switch that exemption off for yourself so the
system can be tested at all.

### Starting spawn

Random position, balanced sides.

True randomness would produce fourteen-to-six with twenty players and break the split from the
start. So the side is not rolled, it is assigned — always to whichever side has fewer people.
Only the position within it is random.

| Constraint | Blocks |
|---|---|
| Minimum distance from the wall | 120 |
| Minimum distance from the world edge | 60 |
| Minimum distance from other players | 150 |

### The world border and the Final War

| | |
|---|---|
| Starting size | 4000 |
| Final size | 160 |
| Default shrink duration | 2h30m |

A red boss bar shows the arena during the Final War: full at 4000, empty at 160. It rebuilds
itself from server state once per second rather than being maintained at start, join and end,
so three cases handle themselves — joining mid-Final-War shows it, leaving removes you from
it, and it comes back after a server restart on its own.

Near a shrinking border it gets uncomfortable: sparks, smoke, impacts and thunder from about
40 blocks out, with strikes inside 12. **The lightning is cosmetic only** — a real strike
would set the forest on fire and kill whoever was standing under it. Anyone dying at the
border should die to the border, not to the decoration in front of it. Once the arena settles
at 160 the effects stop; otherwise the last stand would be a permanent thunderstorm everyone
tuned out after ten minutes.

Nobody respawns outside the border.

### The arena centre

Safe zone, dividing wall and world border all hang off one point. By default that point is
0,0, and `/helden center` moves all three together.

Moving only some of them would be worse than not moving any: leave the wall at X = 0 while the
border sits elsewhere and one side ends up with more land than the other. So there is exactly
one command, and it moves the lot.

The centre is stored with the world, not in the config — a world that has been moved stays
moved across restarts. `/helden reset all` deliberately leaves it alone, for the same reason it
leaves the border size alone: where the arena sits is world layout, not the state of a round.

The config file `mchelden-server.toml` holds where a **fresh** world puts its arena, and it is
what `/helden center reset` returns to. That is the one thing a command cannot cover, because
it has to be set before the world exists.

```toml
[arena]
	center_x = 0
	center_z = 0
```

### Loot and spawns

Four deliberate exceptions to "game systems only". Three of them are data files that modify
vanilla tables instead of replacing them, so other mods and data packs keep working; the
fourth changes a drop chance rather than the loot itself, and is explained below.

**Ancient city trims in shipwrecks.** The two trims that only exist in the ancient city —
`silence` and `ward` — also turn up in all three shipwreck chests, at exactly the chances they
have at home: 1/80 for silence, 4/80 for ward. The numbers are not copied by hand; a test
compares them against Mojang's own `ancient_city.json`, so a vanilla change to those weights
shows up as a failing test. The shipwreck's own `coast` trim is untouched.

**Mending can be fished up.** In vanilla it can, in theory: through a treasure catch, one
book among six entries, randomly enchanted. Multiply those three out and you are into tens of
hours of fishing per book, which over an event that runs for weeks means never. In open water,
every cast now has
a flat **0.25 % chance** of also yielding a mending book — roughly one per four hundred casts,
or about an hour with Lure III. It deliberately does not hang off the treasure catch: loot
modifiers only apply to the outermost table, so the rule sits on `gameplay/fishing` itself and
carries the treasure entry's open-water condition, copied from Mojang's table by a test rather
than by hand. Luck of the Sea does not help — the price of a rule that fits in one sentence.

**Drowned drop their trident three times as often.** Vanilla puts a trident in 6.25 % of
drowned hands and drops it 8.5 % of the time, so roughly one in two hundred drowned yields
one. A drowned that spawns holding a trident now has a **27 % drop chance**, which works out
to 1.7 % overall. This is the one rule that is not a data file: a loot table would be a
*second, independent* roll, so a drowned visibly holding one trident would occasionally drop
two, and the trident would come out factory-fresh instead of worn the way vanilla equipment
drops are. Raising the drop chance keeps it the same drop, just more often — wear and looting
bonus included. Drowned that pick a trident up off the ground are left alone; vanilla already
makes those drop it every time.

**Deserts spawn nothing but endermen.** Every hostile mob is removed from the desert biome,
and the enderman is given the End's pack size of exactly four, so they arrive as often as they
do there. Bats, rabbits and glow squid stay. Note that husks only ever spawned in deserts, so
this takes them out of the game.

---

## Command reference

Everything lives under `/helden` and requires **permission level 2**.

Where a command takes a `<player>`, it accepts an offline player's name too — the game profile
is resolved, not the online entity. `/helden combat clear` is the exception: it targets online
players only, because there is nothing to clear for someone who is not there.

### Players and hearts

| Command | Effect |
|---|---|
| `/helden info <player>` | Hearts, bounty target, playtime remaining, combat timer, active/eliminated |
| `/helden heart give <player> [count]` | Add hearts. `count` 1–4, default 1 |
| `/helden heart remove <player> [count]` | Remove hearts. `count` 1–4, default 1. Hitting zero eliminates |
| `/helden heart set <player> <count>` | Set hearts outright. `count` 0–4 |
| `/helden revive <player> [hearts]` | Undo an elimination. `hearts` 1–4, default 3 |

Heart changes are broadcast. `/helden info` is private to the caller.

### Combat

| Command | Effect |
|---|---|
| `/helden combat clear <player>` | Ends the combat timer for online players. Broadcast |

### Bounty commands

| Command | Effect |
|---|---|
| `/helden bounty roll` | Pairs every known, non-eliminated player. Replaces existing pairings. Plays the wheel for everyone; offline players get it on next join |
| `/helden bounty show <player>` | Prints who that player is paired with |
| `/helden bounty set <player> <target>` | Sets one pairing by hand. Always mutual |
| `/helden bounty clear [player]` | Dissolves that player's pairing — both sides. Without an argument, clears everyone |

### Phase control

| Command | Effect |
|---|---|
| `/helden phase info` | Current phase |
| `/helden phase next` | Advance one phase, with countdown and announcement |
| `/helden phase set <buildup\|war\|finalwar>` | Jump to a phase. Forward gets the staged transition, backward is a silent correction |

A running countdown is replaced rather than queued, so a mistyped command is not irreversible.

### Wall

| Command | Effect |
|---|---|
| `/helden wall drop` | Drops the dividing wall without touching the phase |
| `/helden wall raise` | Puts it back up |

The counterpart to `wall drop` is `wall raise` and not a phase change, on purpose: there must
be no state that only goes forward.

### Final War and border

| Command | Effect |
|---|---|
| `/helden finalwar start [duration]` | Starts the Final War through the phase system, so countdown, storm, dome and border stay one event. Default duration 2h30m |
| `/helden finalwar stop` | Rolls back to War — including mid-countdown |
| `/helden border shrink <size> <duration>` | The bare tool: shrinks the border with no phase and no boss bar. `size` 16–4000 |
| `/helden border reset` | Back to 4000. Needed once on a world created before the size changed — the border is only set on a world's first start |

### Arena centre

| Command | Effect |
|---|---|
| `/helden center` | Where the arena sits, plus where the world border actually is. Warns if the two have drifted apart |
| `/helden center <x> <z>` | Moves safe zone, dividing wall and world border together. Broadcast |
| `/helden center here` | The same, onto your own position |
| `/helden center reset` | Back to the centre configured in `mchelden-server.toml` |

An operator can always set `/worldborder center` by hand, and then the dome no longer sits in
the middle of the world. `/helden center` says so rather than leaving it to be noticed from
the ground.

### Playtime

| Command | Effect |
|---|---|
| `/helden time check <player>` | Time remaining today |
| `/helden time add <player> <minutes>` | Grants time. Negative values take it away. Range −600 to 600 |
| `/helden time set <player> <minutes>` | Sets the **remaining** time to a fixed value. Range 0–600 |

Granted time also stamps the play day, so a gift is not collected back by a reset seconds
later.

### Reset

Resets are **quiet** — only the operator who ran one sees the result. A reset is a correction,
not an announcement, and making `reset hearts <player>` public would put someone on the spot.
Whoever it affects notices through their own state anyway.

**Without a player argument every branch applies globally.**

| Command | Effect |
|---|---|
| `/helden reset hearts [player]` | Back to 3 hearts, elimination lifted. Never grants a fourth — that one is bounty-only |
| `/helden reset bounty [player]` | Dissolves pairings, both sides |
| `/helden reset time [player]` | Playtime used back to zero |
| `/helden reset graves [player]` | Removes graves. **Contents are not dropped** — a reset is not a payout. Registry entries whose block is already gone are cleared too, so the registry heals itself |
| `/helden reset all` | Factory state |

`reset all` asks first: the initial call warns and lists what would be lost, and only a repeat
within **30 seconds** executes. Triggered by accident in week two, the project would be over.
The confirmation window is counted in server ticks rather than wall time, so a confirmation
from before a restart does not carry over, and it is tracked per caller so two operators
cannot take each other's confirmation away.

`reset all` clears graves, all player state, returns the phase to Buildup through the phase
system (so the wall goes back up, the boss bar goes away, the storm stops and the safe zone
returns), and resets the border.

### Debug

Tools for looking at things that are otherwise impossible to observe. Most require a player as
the caller and report only to them.

| Command | Effect |
|---|---|
| `/helden debug combat` | Puts a hit on yourself. The combat HUD cannot be looked at alone otherwise |
| `/helden debug bounty [target]` | Replays the wheel of fortune without changing anything. Rolls onto your actual target by default. Alone on a server it just plays the animation |
| `/helden debug quota` | Drains your quotas so the exhausted state can be looked at |
| `/helden debug playtime` | Toggles your own operator exemption from the time limit. Forgotten on logout — which is what makes it safe: anyone who kicks themselves out with it is back in on the next attempt |
| `/helden debug render` | Asks server *and* client what they know about the wall and the safe zone. If the screen stays blank, this separates the possible causes instead of leaving them to be guessed |
| `/helden debug border` | Why something is or is not happening at the border: size, status, target, remaining time, corners, distance to the edge, effect strength. Three conditions can kill the effect and all three look the same from outside — this tells them apart |
| `/helden debug respawn` | Where you will wake up next death: side, starting point, bed or no bed, world spawn, current position |
| `/helden debug death` | Stages a full player death — death screen, respawn, and the heart animation caught up on respawn. Tests the whole sequence, not just the visuals |
| `/helden debug animation` | Plays the heart-loss effect only. Heart count unchanged, for tuning the visuals without dying every time |

### Duration format

`finalwar start` and `border shrink` take durations like `2h30m`, `150m`, `2h` or `90s`. Each
part is optional but at least one has to be there. **A bare number is rejected, not guessed** —
`3` could mean three hours or three minutes, and the difference is a whole evening. Maximum 12
hours.

---

## Development

```
gradlew runClient
gradlew runServer
gradlew build
gradlew test
```

`gradlew --refresh-dependencies` helps with dependency trouble.

Tests are plain JUnit 5 over the parts that can be checked without starting the game: the
grave split, bounty pairing, the border and storm math, the safe zone geometry, the combat
timer's hit series, the elimination announcement's fit, duration parsing, the playtime tracker
and the persisted state. The loot and spawn tests read Mojang's own data files out of the
NeoForge artifact and compare against them, so a vanilla change to a weight or a mob list
shows up as a failing test rather than as a surprise in-game. Rules that touch every death in the
project and are partly random are not something trying it out in-game can prove.

CI runs `./gradlew build` on JDK 21 for every push and pull request.

The mod uses official Mojang mappings; their license is at
<https://github.com/NeoForged/NeoForm/blob/main/Mojang.md>.

### Layout

| Package | Contents |
|---|---|
| `bounty` | Pairing, roll, resolution |
| `client` | HUDs, renderers, grave screen |
| `combat` | Combat timer, container lock, item quota, death definition |
| `command` | `/helden` and its `reset` subtree |
| `grave` | Block, block entity, estate split, registry |
| `hearts` | Heart count, elimination |
| `mixin` | Death messages, entity collision |
| `network` | Payloads and sync |
| `phase` | Phase transitions and their side effects |
| `playtime` | Daily allowance |
| `state` | Persisted game and player state |
| `text` | All user-facing strings |
| `world` | Wall, safe zone, border, arena centre, storm, spawn placement, boss bar |

`MCHeldenConfig` at the root holds the one server setting; everything else that can change
lives in the world's saved state.

Data files under `src/main/resources/data` carry the loot and spawn rules: a global loot
modifier for the shipwreck trims and two biome modifiers for the desert. NeoForge ships
`add_table` and `remove_spawns` ready-made; `spawn_pack_size` is the mod's own, because
NeoForge can add and remove spawns but not *change* one, and combining the two does not work —
adding runs in an earlier phase than removing, so a mob removed and re-added with a new pack
size is simply gone.

---

## License

GNU General Public License v3 — see [LICENSE.txt](LICENSE.txt).
