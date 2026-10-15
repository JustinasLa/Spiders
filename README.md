# Spiders

> Procedurally animated spiders, staged by the staff of TF-Minecraft.

Spiders brings Cinderweavers to TF-Minecraft: organic dark fantasy spiders with rounded charcoal-brown abdomens, tapered legs, clustered red eyes and curved fangs. Every leg steps, climbs and gallops through procedural animation, so a spider crossing a cave floor, a wall or a ceiling finds its own footing.

Staff spawn, steer, ride and direct the spiders for events and scenes, or hide a mimic among ordinary chests. Anyone nearby can fight back: arrows, thrown items and swings hit the spider and push it away.

## Features

- **Cinderweavers** — hatchling, hunter and broodmother presets, or any size from 0.25 to 2.5.
- **Procedural walking and galloping** — legs plan their own steps over uneven ground; strides lengthen with speed.
- **Walls and ceilings** — climbing spiders walk up walls, across ceilings and over ledges, and drop back to the floor on demand.
- **Pounce** — a spider gallops at a player or a spot and leaps the last few blocks onto it.
- **Mimics** — a dormant chest that wakes when touched, screeches, rises on four legs and gallops away to hide somewhere else.
- **Staff tools** — a menu of seals, guides, whistles, charms and debug tools to place, direct and inspect spiders.
- **Riding** — staff can saddle a spider and drive it, including along walls and ceilings.
- **Getting hit** — arrows, tridents, snowballs, eggs, wind charges and melee swings strike the spider's body and push it.
- **Shroud** — a spider can copy the blocks behind it, as seen by the nearest player, and hide in plain sight.
- **Vanilla rendering** — spiders are drawn with vanilla display entities and need no resource pack or client mod; awake mimics use a small item-model pack in [`resourcepack/`](resourcepack/README.md).

Spiders are staff-operated test entities: they have no health, loot or hostile AI, and they do not survive a restart. Dormant mimics are real chests and do.

## Credits

Spiders is a fork of [TheCymaera's minecraft-spider](https://github.com/TheCymaera/minecraft-spider), made for a video series about procedural animation:
[walking](https://youtu.be/Hc9x1e85L0w), [galloping](https://youtu.be/r70xJytj0sw) and [robots](https://youtu.be/PSnPOYeTW-0).
The TFMC Cinderweavers, mimics, staff tools, climbing, pouncing, hits and shroud were added by [Justinas Launikonis](https://github.com/JustinasLa).

## Documentation

[Project documentation](https://github.com/TF-Minecraft/Docs/blob/main/projects/Spiders/README.md)

Technical documentation is maintained in [TF-Minecraft/Docs](https://github.com/TF-Minecraft/Docs).

## Tests

Run `mvn clean verify` with Java 21. JUnit 5 and Mockito tests cover
staff access, the staff tools, the Cinderweaver model, climbing, pouncing, hits,
mounting, the shroud and mimics, running the spider simulation against mocked worlds.
Surefire writes test results to `target/surefire-reports/`; the Build workflow
uploads them. No coverage gate is enforced. The tests do not start a live Paper
server or check how the spiders look and move on a client.

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
