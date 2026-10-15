# TFMC spiders resource pack

Item models the plugin draws mimics with. Copy `assets/` into the server's
`ItemsAdder/contents/tfmc_spiders/resourcepack/` and run `iazip`.

- `tfmc_spiders:mimic_chest`: the vanilla `minecraft:chest` special model. The first
  custom model data float picks the lid openness, from 0 to 1 in 40 steps.
- `tfmc_spiders:mimic_wood`: a cube of the vanilla chest's plank texture, for the legs.
- `minecraft/atlases/{blocks,items}.json`: puts `entity/chest/normal` in the item atlas so
  the wood model can use it.
- `tfmc_spiders/sounds.json` and `sounds/mimic/*.ogg`: the mimic's screech, shriek, stinger,
  chomp, crack, breath, growl, heartbeat, creak, skitter and gulp, all synthesised by
  [`tools/mimic_sounds.py`](tools/mimic_sounds.py) (no third-party audio). Subtitles are in `lang/en_us.json`.
  The plugin layers vanilla sounds under them, so players without the pack still hear something.
- `tfmc_spiders:mimic_scare` font (`font/mimic_scare.json`, `textures/font/mimic_scare.png`): the
  jump scare, a chest-textured maw drawn by [`tools/mimic_scare.py`](tools/mimic_scare.py). `\ue000`–`\ue002`
  are the same picture at three heights, shown as titles two ticks apart so it lunges at the screen.
  Staff can preview it with `/spider mimic scare`.

Regenerate with `python tools/mimic_sounds.py` (needs numpy, scipy and ffmpeg with libvorbis) and
`python tools/mimic_scare.py` (needs numpy and Pillow); both are seeded, so the output is repeatable.
