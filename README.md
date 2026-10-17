# Spiders

> Procedurally animated spiders, staged by the staff of TF-Minecraft.

Spiders brings Cinderweavers to TF-Minecraft: organic dark fantasy spiders with rounded charcoal-brown abdomens, tapered legs, clustered red eyes and curved fangs. Every leg steps, climbs and gallops through procedural animation, so a spider crossing a cave floor, a wall or a ceiling finds its own footing.

Staff spawn, steer, ride and direct the spiders for events and scenes, or hide a mimic among ordinary chests. Anyone nearby can fight back: arrows, tridents, snowballs, eggs, wind charges and swings hit the spider and push it away.

## Features

- **Cinderweavers** — hatchling, hunter and broodmother presets, or any size from 0.25 to 2.5.
- **Procedural walking and galloping** — legs plan their own steps over uneven ground; strides lengthen with speed.
- **Walls and ceilings** — climbing spiders walk up walls, across ceilings and over ledges, and drop back to the floor on demand.
- **Pounce** — a spider gallops at a player or a spot and leaps the last few blocks onto it.
- **Mimics** — a dormant chest that breathes, turns to face you when you look away and peeks at you with red eyes. Touch it and it cracks its lid, springs at your face with a full-screen jump scare and a scream, unfolds on four cracking legs and hunts you for ten seconds, biting, while your heart pounds and the world goes dark. Then it stops, cocks its head at you, and gallops away to hide as another chest. Linger beside one and it takes you anyway.
- **Staff tools** — a menu of seals, guides, whistles, charms and debug tools to place, direct and inspect spiders. Tools reach spiders within 64 blocks, and the debug view is shown only to the staff member who turns it on.
- **Riding** — staff right-click a spider to ride and drive it, including along walls and ceilings. No saddle shows, so a vanished rider leaves nothing visible.
- **Getting hit** — arrows, tridents, snowballs, eggs, wind charges and melee swings strike the spider's body and push it, stopping at walls.
- **Shroud** — a spider can copy the blocks behind it, as seen by the nearest player, and hide in plain sight.
- **Vanilla rendering** — spiders are drawn with vanilla display entities and need no resource pack or client mod; mimics use a small pack in [`resourcepack/`](resourcepack/README.md) for the chest model, their sounds and the jump scare.

Spiders are staff-operated test entities: they have no health or loot, and they do not survive a restart. A spider with no player within 96 blocks, or in an unloaded chunk, sleeps until someone comes back. Dormant mimics are real chests and do. Awake mimics are the one hostile thing: they hunt whoever woke or hit them, but a bite never takes a player's last heart.

## Credits

Spiders is a fork of [TheCymaera's minecraft-spider](https://github.com/TheCymaera/minecraft-spider), made for a video series about procedural animation:
[walking](https://youtu.be/Hc9x1e85L0w), [galloping](https://youtu.be/r70xJytj0sw) and [robots](https://youtu.be/PSnPOYeTW-0).
The TFMC Cinderweavers, mimics, staff tools, climbing, pouncing, hits and shroud were added by [Justinas Launikonis](https://github.com/JustinasLa).

## Documentation

[Project documentation](https://github.com/TF-Minecraft/Docs/blob/main/projects/Spiders/README.md)

Technical documentation is maintained in [TF-Minecraft/Docs](https://github.com/TF-Minecraft/Docs).

## Tests

Run `mvn clean verify` with Java 21. JUnit 5 and Mockito tests cover every
part of the plugin, running the spider simulation against mocked worlds; a
test-only registry stub lets sounds and other registry constants load without a
server. JaCoCo fails the build unless every instruction and branch is covered;
only the mimic classes are left out of that gate (their few remaining misses
are unreachable code). Reports are written to `target/site/jacoco/`, and
Surefire's test results to `target/surefire-reports/`, which the Build workflow
uploads. The tests do not start a live Paper server or check how the spiders
look and move on a client.

## License

Copyright (c) 2026 Justinas Launikonis.
Copyright (c) 2026 TF-Minecraft contributors.

TF-Minecraft-authored material in this repository is licensed under the
[Artistic License 2.0](LICENSE).

Code from the upstream [minecraft-spider](https://github.com/TheCymaera/minecraft-spider)
project by TheCymaera remains under its published terms: the plugin and source
code may be used for commercial and non-commercial purposes; attribution is
appreciated but not required; do not resell it without substantial changes.

Third-party dependencies retain their own licenses.
