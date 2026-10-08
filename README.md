# Mana Essence Bridge

A Botania addon that bridges Botania mana and Mystical Agriculture essence: an upgraded Mana Pool becomes a two-way exchange, and eight new Botania flowers grow, harvest, guard and feed an essence farm.

[Download on CurseForge](https://www.curseforge.com/minecraft/mc-mods/mana-essence-bridge) · Minecraft **1.16.5**, **1.18.2**, **1.19.2** and **1.20.1**, Forge

## How it works

Craft an **Inferium Mana Catalyst** and right-click it onto a plain Mana Pool. The pool is now upgraded and can:

| Action | Result |
| --- | --- |
| Right-click with essence | convert one |
| Shift + right-click with essence | convert the whole stack |
| Shift + right-click, empty hand | buy essence back with mana |
| Hopper or pipe into the pool | the same, automated |
| Right-click with an Essence Siphon | the pool starts pulling essence from adjacent chests (redstone pauses it) |
| Shift + right-click with a Resonance Crystal | strip the top tier and get the catalyst back |

## Six tiers

Each catalyst unlocks the next essence level, demands its own rung of Botania progression and doubles the pool's capacity:

| Tier | Essence | Mana given | Mana to buy | Pool holds | Crafted at |
| --- | --- | --- | --- | --- | --- |
| 1 | Inferium | 2,000 | 2,500 | 1M | Crafting table |
| 2 | Prudentium | 8,000 | 10,000 | 2M | Petal Apothecary |
| 3 | Tertium | 32,000 | 40,000 | 4M | Runic Altar |
| 4 | Imperium | 128,000 | 160,000 | 8M | Elven Trade |
| 5 | Supremium | 512,000 | 640,000 | 16M | Terra Plate |
| 6 | Insanium | 2,048,000 | 2,560,000 | 32M | Runic Altar |

"Pool holds" is for a plain Mana Pool; Diluted and Fabulous pools scale from their own size. **Expander Crystals** placed against the pool add 25% more each, up to 8 per pool. **Tier 6 exists only with Mystical Agradditions installed.**

Catalysts apply strictly in order, and the higher tiers are out of reach until you have smelted Terrasteel, opened the Alfheim portal and beaten the Gaia Guardian — and, for tier 6, the Gaia Guardian II. Until a rung is cleared, its entry stays hidden in the Lexica Botania and its catalyst in JEI.

## Automation

- **Essence Siphon** — the pool pulls essence out of the chests touching it.
- **Essence Condenser** — the buy-back, automated: turns pool mana into the chosen essence and pushes it into an adjacent chest, hopper or pipe. A reserve keeps part of the pool untouched; surplus mode only uses mana the pool has no room for. Works with a pool touching it or one up to 12 blocks away, bound with the Wand of the Forest.
- **Mana Fertilizer** — Fertilized Essence steeped in a Mana Pool: grows every Mystical Agriculture crop in a 3x3 by two stages, by hand or from a dispenser. The Greater Mana Fertilizer, from the Terrestrial Agglomeration Plate, ripens a whole 5x5 at once.
- **Pool network** — link your upgraded pools with an Essence Mirror (Shift + right-click), and mana evens out between them once a second, however far apart they are.
- **Condensation Lens** — on a Mana Spreader aimed at an upgraded pool, bursts turn straight into Inferium instead of filling the pool.

## Flowers

| Flower | Type | What it does |
| --- | --- | --- |
| **Mysticarnation** | functional | an Agricarnation for Mystical Agriculture crops |
| **Reaperbloom** | functional | harvests ripe Mystical Agriculture crops in an 11x11 area and replants them |
| **Wardenia** | functional | guards any farm: keeps farmland moist, stops trampling, pushes hostile mobs out, stops their projectiles, snuffs out explosions |
| **Essentide** | generating | eats resource essences — the higher the crop tier, the more mana |
| **Brookbell** | generating | mana from items drifting past in flowing water, without taking them |
| **Boltbloom** | generating | catches lightning, and in a thunderstorm calls harmless lightning onto itself |
| **Melodia** | generating | listens to note blocks; varied tunes pay more than one note on repeat |
| **Bumblebloom** | generating | bees pollinate it, and every visit makes mana |

Every flower also comes in a floating version on a Botania island: the flower plus any floating flower. Floating flowers spin and bob like Botania's, and the Manaseer Monocle shows every flower's radius.

## Tools

- **Essence Mirror** — bind it to an upgraded pool and trade with it from anywhere in that dimension: right-click opens the pool's window (mana, prices, any essence to buy), Shift + right-click sends all your essence into the pool.
- **Mana Ledger** — every upgraded pool you own in one window: tier, coordinates, distance and direction, mana, fill rate, which condenser draws from it and its pool-network links (hover a pool to light up the pools it is linked to).
- **Resonance Crystal** — strips the top tier off a pool and returns that catalyst intact, for mana from the pool itself. Sneaking is required, so a stray click can't undo your progress.

A mined upgraded pool keeps its tier and its mana in the item, so it can be moved without losing anything.

## Why exactly x4

The exchange rate deliberately mirrors Mystical Agriculture's own ladder: 4 essences make 1 of the next tier, and the same in reverse. That isn't laziness, it's arithmetic — the Master Infusion Crystal has infinite durability, so climbing that ladder costs the player nothing. Any rate better than x4 becomes an infinite mana loop: buy cheap essence, craft it upward for free, sell it higher. At exactly x4 the loop pays nothing.

Buying back costs 25% more than selling, so the pool doesn't become free storage. The flowers follow the same rule: growing a crop with Mysticarnations always costs more mana than the Essentide gets back from its essence.

## Configuration

`config/manaessencebridge-common.toml`:

| Option | Default | What it does |
| --- | --- | --- |
| `manaPerInferium` | 2000 | mana per Inferium Essence; every tier above is worth 4x more |
| `buyMarkupPercent` | 125 | buy-back price as a percent of the sell price |
| `maxTier` | 5 | highest of the first five tiers the mod will handle |
| `enableInsanium` | true | tier 6, when Mystical Agradditions is installed |
| `boostCapacity` | true | upgraded pools hold x2 mana per tier above the first |
| `buyHighestTier` | true | empty-hand buying: the best tier you can afford, or always Inferium |
| `automationEnabled` | true | accept essence from hoppers and pipes |
| `pullFromContainers` | true | server-wide switch for the Essence Siphon |
| `condenserIntervalTicks` | 2 | how often a condenser makes one essence |
| `privatePools` | false | only a pool's owner (and friends added with `/manabridge trust add`) can buy from it, strip it or tie a condenser to it |
| `poolLinkRate` | 50000 | mana a pool-network link moves per second |
| `keepManaOnBreak` | true | a mined upgraded pool keeps its mana |
| `expanderPercent` | 25 | capacity each Expander Crystal adds |
| `expanderMaxCrystals` | 8 | Expander Crystals counted per pool |
| `manaFertilizerStages` | 2 | growth stages a Mana Fertilizer gives each crop |
| `poolGlow` | true | tier-coloured sparks over upgraded pools (client-side) |
| `showHud` | true | on-screen readout when looking at a pool |

Every flower number is in the `[flowers]` section. The x4 step itself is deliberately not configurable, for the reason above.

## For modpack makers: custom exchange rates

Upgraded pools can take more than Mystical Agriculture essence. A datapack can add any item or item tag, with its own mana value and the pool tier it needs - put JSON files into `data/<namespace>/manaessencebridge_exchange/`:

```json
{
  "required_mod": "thermal",
  "entries": [
    { "item": "minecraft:diamond", "mana": 40000, "tier": 2 },
    { "tag": "forge:gems/emerald", "mana": 30000 }
  ]
}
```

`tier` defaults to 1, `required_mod` is optional (the file is skipped when that mod is missing), and a single entry can also sit at the root of the file. These items are accepted everywhere essence is - by hand, from hoppers and pipes, through the Essence Siphon and the Essence Mirror - and show their value in the tooltip. They are **sell-only**: a pool never gives them back, so they can't form a buy-low/sell-high loop through this mod. Pricing them is up to the pack; Mystical Agriculture essences always keep their own x4 ladder.

## Compatibility

This mod uses **no Mixins**. Everything goes through Botania's public API (`IManaPool` on 1.16.5 and 1.18.2, `ManaPool` on 1.19.2 and 1.20.1) and ordinary Forge events — no third-party bytecode is touched. The one exception is client-side: to show flower radii under the Manaseer Monocle, the flower renderer calls three public static helpers of Botania's own flower renderer and monocle. If a Botania version lacks them, the radius simply isn't drawn.

**Requires:** Botania and Mystical Agriculture. **Optional:** Mystical Agradditions (tier 6), JEI, Jade, The One Probe.

## Repository layout

Four independent Gradle projects, one per Minecraft version:

```
.            <- Minecraft 1.16.5, Forge 36.2.34, JDK 8
1.18.2/      <- Minecraft 1.18.2, Forge 40.3.12, JDK 17
1.19.2/      <- Minecraft 1.19.2, Forge 43.5.2,  JDK 17
1.20.1/      <- Minecraft 1.20.1, Forge 47.4.23, JDK 17
```

They share the design but not the code: between 1.16.5 and the others Minecraft renamed most of its classes, Forge replaced the capability system, JEI rewrote its plugin API, and Patchouli changed how books load content twice over. The 1.19.2 and 1.20.1 trees are near-identical and differ only in rendering (`PoseStack` vs `GuiGraphics`), creative-tab registration and library versions. The 1.18.2 tree is the 1.19.2 code with Botania's older API names (`IManaPool`, `TileEntityFunctionalFlower`), Jade 5 and JEI 10. Each tree is self-contained.

## Building

Each project builds with its own wrapper:

```
gradlew build          # 1.16.5, from the repository root
cd 1.18.2 && gradlew build
cd 1.19.2 && gradlew build
cd 1.20.1 && gradlew build
```

The jar lands in `build/libs/`.

All four `gradle.properties` files pin `org.gradle.java.home` to a local JDK path, because the two versions need different Java releases (8 and 17) and the machine they were written on has neither as its default. **Change that line to your own JDK path**, or delete it and run Gradle with the right `JAVA_HOME`.

Botania, JEI, Jade and The One Probe are pulled as `compileOnly` dependencies from CurseMaven (Modrinth Maven for 1.18.2), pinned by file id — no manual jar wrangling needed. Mystical Agriculture and Mystical Agradditions are not compile dependencies at all: their essences and crops are looked up by item id and reflection at runtime.

## License

MIT — see [LICENSE](LICENSE).

The mod bundles no third-party code: Botania is used through its API, which its license explicitly exempts from the copyleft clause, plus calls (not copies) to the client helpers above; floating flowers reuse Botania's own model loader and island models by name. Mystical Agriculture and JEI are MIT.
