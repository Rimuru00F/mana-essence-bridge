# Mana Essence Bridge

A Botania addon that converts Mystical Agriculture essence into mana and back, through an upgraded Mana Pool.

[Download on CurseForge](https://www.curseforge.com/minecraft/mc-mods/mana-essence-bridge) · Minecraft **1.16.5**, **1.19.2** and **1.20.1**, Forge

No new blocks — the mod extends the Mana Pool you already have.

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

## Five tiers

Each catalyst unlocks the next essence level and demands its own rung of Botania progression:

| Tier | Essence | Mana given | Mana to buy | Crafted at |
| --- | --- | --- | --- | --- |
| 1 | Inferium | 2,000 | 2,500 | Crafting table |
| 2 | Prudentium | 8,000 | 10,000 | Petal Apothecary |
| 3 | Tertium | 32,000 | 40,000 | Runic Altar |
| 4 | Imperium | 128,000 | 160,000 | Elven Trade |
| 5 | Supremium | 512,000 | 640,000 | Terra Plate |

Catalysts apply strictly in order, and the higher tiers are out of reach until you have smelted Terrasteel, opened the Alfheim portal and beaten the Gaia Guardian. Until a rung is cleared, its entry stays hidden in the Lexica Botania.

## Why exactly x4

The exchange rate deliberately mirrors Mystical Agriculture's own ladder: 4 essences make 1 of the next tier, and the same in reverse. That isn't laziness, it's arithmetic — the Master Infusion Crystal has infinite durability, so climbing that ladder costs the player nothing. Any rate better than x4 becomes an infinite mana loop: buy cheap essence, craft it upward for free, sell it higher. At exactly x4 the loop pays nothing.

Buying back costs 25% more than selling, so the pool doesn't become free storage.

## Changed your mind?

The **Resonance Crystal** strips the top tier off a pool and returns that catalyst intact. It is not consumed — the price is mana from the pool itself, the same as buying one essence of that tier. Sneaking is required, so a stray click can't undo your progress.

A pool mined while upgraded keeps its tier in the item, and the tooltip says which one, so an upgraded pool can be carried elsewhere without losing anything.

Pools are tinted by tier, the colours taken from Mystical Agriculture's own essence textures, and the particles that burst out on every conversion use the same colours. Hovering an essence shows what it is worth in an upgraded pool.

## Configuration

`config/manaessencebridge-common.toml`:

| Option | Default | What it does |
| --- | --- | --- |
| `manaPerInferium` | 2000 | mana per Inferium Essence; every tier above is worth 4x more |
| `buyMarkupPercent` | 125 | buy-back price as a percent of the sell price |
| `maxTier` | 5 | highest tier the mod will handle |
| `buyHighestTier` | true | empty-hand buying: the best tier you can afford, or always Inferium |
| `automationEnabled` | true | accept essence from hoppers and pipes |
| `pullFromContainers` | true | server-wide switch for auto-pull; each pool is switched on with an Essence Siphon |
| `pullIntervalTicks` | 20 | how often they check, in ticks (20 = once a second) |
| `hideLockedInJei` | true | hide catalysts for gates you haven't passed |
| `showHud` | true | on-screen readout when looking at a pool |

The x4 step itself is deliberately not configurable, for the reason above.

## Compatibility

This mod uses **no Mixins**. Everything goes through Botania's public API (`IManaPool` on 1.16.5, `ManaPool` on 1.19.2 and 1.20.1) and ordinary Forge events — no third-party bytecode is touched.

**Requires:** Botania and Mystical Agriculture. **Optional:** JEI, Jade, The One Probe.

## Repository layout

Three independent Gradle projects, one per Minecraft version:

```
.            <- Minecraft 1.16.5, Forge 36.2.34, JDK 8
1.19.2/      <- Minecraft 1.19.2, Forge 43.5.2,  JDK 17
1.20.1/      <- Minecraft 1.20.1, Forge 47.4.23, JDK 17
```

They share the design but not the code: between 1.16.5 and the others Minecraft renamed most of its classes, Forge replaced the capability system, JEI rewrote its plugin API, and Patchouli changed how books load content twice over. The 1.19.2 and 1.20.1 trees are near-identical and differ only in rendering (`PoseStack` vs `GuiGraphics`), creative-tab registration and library versions. Each tree is self-contained.

## Building

Each project builds with its own wrapper:

```
gradlew build          # 1.16.5, from the repository root
cd 1.19.2 && gradlew build
cd 1.20.1 && gradlew build
```

The jar lands in `build/libs/`.

All three `gradle.properties` files pin `org.gradle.java.home` to a local JDK path, because the two versions need different Java releases (8 and 17) and the machine they were written on has neither as its default. **Change that line to your own JDK path**, or delete it and run Gradle with the right `JAVA_HOME`.

Botania and JEI are pulled from CurseMaven as `compileOnly` dependencies, pinned by project and file id — no manual jar wrangling needed. Mystical Agriculture is not a compile dependency at all: its essences are looked up by item id at runtime.

## License

MIT — see [LICENSE](LICENSE).

The mod bundles no third-party code. Botania is used through its API only, which its license explicitly exempts from the copyleft clause; Mystical Agriculture and JEI are MIT.
