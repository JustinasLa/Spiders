# Spider

## TFMC fork: Cinderweavers

This fork targets **Paper 1.21.10 / Java 21+**. It provides staff-operated,
procedurally animated organic dark fantasy spiders: rounded charcoal-brown
abdomens, low cephalothoraxes, tapered legs, sparse bristles, eight clustered
eyes with subdued red eyeshine, and curved fangs. There are no mechanical parts,
flashing lenses or exhaust particles.
The design is a new TFMC concept, not an assertion of established lore.

Only **`tfmc.spider.staff`** grants access (default: server operators).
The permission is checked on commands, use of copied/borrowed tools, held-item
steering, saddle interactions and actual mounting. Riders lose control and
dismount when permission is removed. No permission-manager changes are needed
for operators; granting access to other staff is a separate server policy choice.
Hitting a spider (see "Getting hit") is open to everyone.

Quick start, as authorised staff:

```text
/spider spawn hatchling
/spider spawn hunter
/spider spawn broodmother
/spider spawn 1.2
/spider gait gallop
/spider climb on
/spider speed gallop 8
/spider pounce <player>
/spider tools
/spider list
/spider scale 1.2
/spider remove
/spider remove <id>
/spider remove all
```

Aim at a solid surface within 32 blocks when spawning. Console or staff can also
use `/spider spawn hunter at <world> <x> <y> <z>` in an already loaded chunk.
Hatchling, hunter and broodmother scales are 0.35, 0.8 and 1.5; `/spider spawn`
also accepts any scale from 0.25 to 2.5 (for example `/spider spawn 2.2`), and
`/spider scale` resizes a spawned spider within the same range. Up to six spiders
may be active at once during Dev testing.

**Walls and ceilings.** `/spider climb on` (or the Clinging Charm) lets the
targeted spider walk up walls, across ceilings and over ledges; `/spider climb off`
lets it drop back to the floor. Aiming `/spider spawn` or the Summoning Seal at a
wall or ceiling spawns the spider clinging to that face with climbing already on.
Climbing applies to both gaits.

**Gallop.** `/spider gait gallop` or `/spider gait walk` (or the Hunting Instinct
tool) switches the targeted spider's gait. A rider can also hold the sprint key
while moving to gallop, returning to a walk when they let go.

**Speed.** `/spider speed gallop <blocks/s>` and `/spider speed walk <blocks/s>`
set the targeted spider's top speed (0.5–20); without a number they show the
current speed. Strides lengthen with speed so the legs keep up. Very small
spiders fall short of the higher settings.

**Pounce.** `/spider pounce <player> [blocks/s]` makes the targeted spider gallop
at that player and leap the last few blocks, landing on them. The optional number sets the gallop
speed first. You are told where it landed, or why it gave up: the player
left, logged off, went into spectator, moved more than 96 blocks away, or 20
seconds passed. `/spider pounce at <x> <y> <z> [blocks/s]` pounces at a spot
in the spider's world instead, e.g. for staged scenes or from the console.
`/spider pounce stop` cancels it. A climbing spider pounces
along its wall or ceiling and drops onto a player below. The leap can be tuned
with `/spider options spider_options.pounce.leapRange|leapBoost|leapLift|giveUpTicks`.
Pounces are animation only: they do not damage anyone.

**Getting hit.** Anyone can hit a spider, staff or not, and so can mobs and
dispensers. Arrows, spectral arrows and tridents strike its actual body and
legs and bounce off; snowballs and eggs break on it; wind charges burst on it.
Players can also hit it by swinging at it within their reach. Each hit pushes
the spider, plays its hurt sound and breaks its shroud. From weakest to
strongest: snowball or egg, arrow, sword or fist (scaled by attack charge),
trident, wind charge. A barely drawn bow or a spammed swing only glances off.
Projectiles pass through the hidden saddle seat, and hitting the seat counts as
hitting the spider. A rider's own shots and swings pass through their mount.
Hits do no damage: spiders have no health.

**Shroud Charm.** The charm or `/spider shroud on|off` shrouds the targeted spider. The shroud copies whatever lies behind each part of the
spider as seen by the nearest player within 48 blocks (spectators excluded),
including water and lava surfaces. With nobody near, it copies the ground below. It uses
blocks at their true colour, lit like the world around them. The old dimmed
matching, which looks washed out with bright gamma, is still available with
`/spider options spider_options.cloak.allowCustomBrightness set true`.

**Mimic.** `/spider mimic place` (or `/spider spawn mimic`) puts a dormant mimic
on the floor you aim at, facing you: a real chest block, so it looks exactly like
one and stays put through restarts. Anyone who right-clicks or hits it wakes it.
The chest bursts open with a loud screech that carries about 60 blocks, rises on
four long legs, turns its back on whoever woke it and gallops away at 7 blocks/s,
lid flapping. It keeps running directly away from that player (around obstacles
if it gets stuck) until it's 250 blocks from them, then sits back down as a chest
for them to hunt down. The plugin keeps the chunks it runs through loaded, but it
stops early rather than enter terrain that has never been generated. Anything
hoppered or stuffed inside is spat out. Woken by the console, with no player to
flee, it stays put and settles back into a chest after 20 idle seconds. It can't be opened,
emptied by hoppers or blown up, and only staff can break it (in creative mode, to
remove it). `/spider mimic wake` and `/spider mimic sleep` do the same by command;
console uses `/spider mimic place|wake at <world> <x> <y> <z>`. Awake mimics count
towards the six-creature limit and take the usual staff steering, gait and pounce
commands; they stay chest-sized and don't climb. A mimic that is awake when the
server stops drops back into a chest where it stands.

Commands that act on one spider use the spider linked to the Summoning Seal in
your hand, otherwise the nearest spider in your world.

Take controls from `/spider tools`. Right-click the Summoning Seal to create or
recall its linked spider. Hold the Brood Guide and aim at terrain to guide the
spiders; hold the Broodcall Whistle to call them towards you. Spiders follow the
nearest staff beacon in the same world, using the upstream steering system. A saddle
on a spider's hidden seat allows staff to mount it and drive with movement keys;
sneak to dismount. On a wall or ceiling the rider steers by looking: forward is
the look direction flattened onto the surface. The other tools inspect joints,
switch gait or wall climbing, or show the animation debug view. These spiders do
not yet have hostile dungeon AI, health, loot, eggs or persistent nests.

Existing seals containing a clockwork Cinderweaver automatically receive the
organic appearance and sounds while keeping their scale and edited leg rig.
Take fresh tools from `/spider tools` for the new names and item appearances.

All visuals are vanilla display entities. **Cinderweavers need no resource pack,
client mod or other plugin.** Mimics draw the vanilla chest and its wood through the
small item-model pack in [`resourcepack/`](resourcepack/README.md); install it with
ItemsAdder (or any server pack) or awake mimics show as missing models. Spiders are temporary test entities: removing
them or stopping the server cleans up their displays and seats. They do not
survive a restart. The renderer uses its own `tfmc_spider_render` entity tag.

Build and run the permission, model, climbing, pounce, hit, shroud and mimic tests with `./gradlew build` (`gradlew.bat build`
on Windows), using JDK 21. Install the **`tfmc-spiders-4.0.0-tfmc.14.jar`** shaded
artifact; the `*-plain.jar` is not the deployable plugin. Restart the test server
to load it. This is experimental multiplayer software; test movement and client
performance with a few spiders before increasing any limits.

The original project's documentation and licence terms follow below. Its old
standalone command examples predate the current `/spider ...` command tree.

## Introduction
This plugin was developed for a video series about procedural animations.
1. Procedural Walking Animation: https://youtu.be/Hc9x1e85L0w
2. Procedural Galloping Animation: https://youtu.be/r70xJytj0sw
3. Procedurally Animated Robots: https://youtu.be/PSnPOYeTW-0


This plugin is very experimental and untested in multiplayer. Use at your own risk.



## Installation
1. Download the JAR from the [releases page](https://github.com/TheCymaera/minecraft-spider/releases/).
2. Set up a [Paper](https://papermc.io/downloads) or [Spigot](https://getbukkit.org/download/spigot) server. (Instructions below)
3. Add the JAR to the `plugins` folder.
4. Download the world folder from [Planet Minecraft](https://www.planetminecraft.com/project/spider-garden/).
5. Place the world folder in the server directory. Name it `world`.

## Running a Server
1. Download a server JAR from [Paper](https://papermc.io/downloads) or [Spigot](https://getbukkit.org/download/spigot).
2. Run the following command `java -Xmx1024M -Xms1024M -jar server.jar nogui`.
3. I typically use the Java runtime bundled with my Minecraft installation so as to avoid version conflicts.
   - In Modrinth, you can find the Java runtime location inside the profile options menu.
4. Accept the EULA by changing `eula=false` to `eula=true` in the `eula.txt` file.
5. Join the server with `localhost` as the IP address.


## Commands
Autocomplete will show available options.

Get control items:
```
/items
```

Load preset:
```
/preset <name:string> <segment_length:double?> <segment_count:int?>
/preset hexbot 1 4
```

Load torso or leg model
```
/torso_model <name:string>
/leg_model <name:string>
```

Modify or get options
```
/options gait maxSpeed 3

/options gait maxSpeed
```

Scale the spider
```
/scale 2
```

Play splay animation (Spider must be spawned)
```
/splay
```

Change eye or blinking lights palette
```
/animated_palette eye cyan_blinking_lights
/animated_palette blinking_lights red_blinking_lights

# Custom palette (block_id, block_brightness, sky_brightness)+
/animated_palette eye custom minecraft:stone 15 15 minecraft:redstone_block 15 15
```

Fine-grained model modification
```
/modify_model <...selectors> <...operations>

# Selectors can be either a block id or a tag
# e.g. Select all cloaks in the torso
/modify_model cloak torso ...

# e.g. Select diamond blocks and netherite blocks
/modify_model minecraft:diamond_block or minecraft:netherite_block ...

# Set block
/modify_model cloak set_block minecraft:gray_concrete

# Set brightness
/modify_model cloak brightness 0 7

# Copy block from the world
/modify_model cloak copy_block ~ ~1 ~
```

Stealth Variant Example:
```
/torso_model stealth
/scale 1.3
/modify_model cloak set_block minecraft:gray_concrete
/modify_model cloak or minecraft:netherite_block or minecraft:cauldron or minecraft:anvil or minecraft:gray_shulker_box brightness 0 7

# You may need to pick a different brightness depending on your shaders
```

Copy block examples used in the video:
```
/modify_model cloak torso copy_block ~ ~1 ~
/modify_model cloak tibia copy_block ~ ~1 ~
/modify_model cloak tip copy_block ~ ~1 ~
```

## Development
1. Clone or download the repo.
2. Run Gradle `build` or `buildDependents` to build the plugin. The JAR will be created in `build/libs`.
3. To develop on an existing server, create a symlink and add it to the server `plugins` folder.
   - Windows: `mklink /D newFile.jar originalFile.jar`
   - Mac/Linux: `ln -s originalFile.jar newFile.jar `
4. You can configure hot swapping by following this [tutorial](https://www.youtube.com/watch?v=yQVLT6sDg68).

## License
You may use the plugin and source code for both commercial or non-commercial purposes.

Attribution is appreciated but not due.

Do not resell without making substantial changes.
