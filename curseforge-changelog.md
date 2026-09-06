## 1.16.5.1 - First release

Initial public release for Minecraft 1.16.5 / Forge.

### What's in it

- An upgraded Botania Mana Pool converts Mystical Agriculture essence into mana and back
- Five tiers - Inferium, Prudentium, Tertium, Imperium, Supremium - each worth exactly 4x the one below
- Catalysts are crafted at Botania's own stations: Petal Apothecary, Runic Altar, Elven Trade and Terra Plate
- Higher tiers stay locked until you have Terrasteel, an open Alfheim portal and a defeated Gaia Guardian
- Hoppers and pipes can feed essence into an upgraded pool
- Pools are tinted by tier, with an on-screen readout of tier, exchange rate and stored mana
- Upgrades survive breaking and replacing the pool
- JEI tab with every exchange rate, Lexica Botania entries and six advancements
- Rates and behaviour configurable in `config/manaessencebridge-common.toml`
- English and Russian localization

### Requirements

Botania and Mystical Agriculture are required. JEI is optional.

No Mixins are used - the mod works entirely through Botania's public API and Forge events.
