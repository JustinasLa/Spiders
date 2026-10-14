# TFMC spiders resource pack

Item models the plugin draws mimics with. Copy `assets/` into the server's
`ItemsAdder/contents/tfmc_spiders/resourcepack/` and run `iazip`.

- `tfmc_spiders:mimic_chest`: the vanilla `minecraft:chest` special model. The first
  custom model data float picks the lid openness, from 0 to 1 in 40 steps.
- `tfmc_spiders:mimic_wood`: a cube of the vanilla chest's plank texture, for the legs.
- `minecraft/atlases/{blocks,items}.json`: puts `entity/chest/normal` in the item atlas so
  the wood model can use it.
