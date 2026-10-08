## Mana Essence Bridge

**A bridge between Botania mana and Mystical Agriculture essence - and a set of Botania flowers built for essence farms.**

Your Inferium farm runs around the clock while your mana pools sit half empty? Or the other way round - endoflames burning fuel into nothing while you're short on essence? This addon merges the two economies: an upgraded Mana Pool becomes a two-way exchange, and eight new flowers grow, harvest, guard and feed your farm.

### How it works

Craft an **Inferium Mana Catalyst** and right-click it onto a plain Mana Pool. The pool is now upgraded and can:

| Action | Result |
| --- | --- |
| Right-click with essence | convert one |
| Shift + right-click with essence | convert the whole stack |
| Shift + right-click, empty hand | buy essence back with mana |
| Hopper or pipe into the pool | the same, automated |
| Right-click with an Essence Siphon | the pool starts pulling essence from adjacent chests (redstone pauses it) |
| Shift + right-click with a Resonance Crystal | strip the top tier and get the catalyst back |

### Six tiers

Each catalyst unlocks the next essence level, demands its own rung of Botania progression and doubles the pool's capacity:

| Tier | Essence | Mana given | Mana to buy | Pool holds | Crafted at |
| --- | --- | --- | --- | --- | --- |
| 1 | Inferium | 2,000 | 2,500 | 1M | Crafting table |
| 2 | Prudentium | 8,000 | 10,000 | 2M | Petal Apothecary |
| 3 | Tertium | 32,000 | 40,000 | 4M | Runic Altar |
| 4 | Imperium | 128,000 | 160,000 | 8M | Elven Trade |
| 5 | Supremium | 512,000 | 640,000 | 16M | Terra Plate |
| 6 | Insanium | 2,048,000 | 2,560,000 | 32M | Runic Altar |

"Pool holds" is for a plain Mana Pool; Diluted and Fabulous pools scale from their own size. **Expander Crystals** placed against the pool add 25% more each, up to 8 per pool. **Tier 6 exists only with Mystical Agradditions installed** - without it, Insanium doesn't show up anywhere.

No skipping ahead: catalysts apply strictly in order, and the higher tiers are physically out of reach until you have smelted Terrasteel, opened the Alfheim portal, beaten the Gaia Guardian - and, for tier 6, the Gaia Guardian II. Until a rung is cleared, its entry stays hidden in the Lexica Botania and its catalyst stays hidden in JEI.

### Automation

- **Essence Siphon** - the pool pulls essence out of the chests touching it.
- **Essence Condenser** - the buy-back, automated. Pick a tier with an essence, and the condenser turns pool mana into essence and pushes it into a chest, hopper or pipe next to it. A **reserve** of 0-75% keeps part of the pool untouched; **surplus mode** only uses mana the pool has no room for. Works with a pool touching it or one up to 12 blocks away, bound with the Wand of the Forest.
- **Mana Fertilizer** - Fertilized Essence steeped in a Mana Pool: grows every Mystical Agriculture crop in a 3x3 by two stages, by hand or from a dispenser. The **Greater** one, from the Terrestrial Agglomeration Plate, ripens a whole 5x5 at once.
- **Pool network** - link your upgraded pools with an Essence Mirror (Shift + right-click), and mana evens out between them once a second, however far apart they are.
- **Condensation Lens** - on a Mana Spreader aimed at an upgraded pool, bursts turn straight into Inferium instead of filling the pool.

### Flowers

| Flower | Type | What it does |
| --- | --- | --- |
| **Mysticarnation** | functional | an Agricarnation for Mystical Agriculture crops |
| **Reaperbloom** | functional | harvests ripe Mystical Agriculture crops in an 11x11 area and replants them |
| **Wardenia** | functional | guards any farm: keeps farmland moist, stops trampling, pushes hostile mobs out, stops their projectiles, snuffs out explosions |
| **Essentide** | generating | eats resource essences - the higher the crop tier, the more mana |
| **Brookbell** | generating | mana from items drifting past in flowing water, without taking them |
| **Boltbloom** | generating | catches lightning, and in a thunderstorm calls harmless lightning onto itself |
| **Melodia** | generating | listens to note blocks; varied tunes pay more than one note on repeat |
| **Bumblebloom** | generating | bees pollinate it, and every visit makes mana |

Every flower also comes in a **floating** version on a Botania island - the flower plus any floating flower. Jade and The One Probe show what each flower is doing - digesting, resting, waiting for lightning, mana made in the last second.

### Tools

- **Essence Mirror** - bind it to an upgraded pool and trade with it from anywhere in that dimension: right-click opens the pool's window - mana, prices, any essence to buy - and Shift + right-click sends all your essence into the pool.
- **Mana Ledger** - every upgraded pool you own in one window: tier, coordinates, distance and a direction arrow, mana, how fast it fills or drains, which condenser draws from it, and its pool-network links (hover a pool to light up the pools it is linked to). Pools in unloaded chunks show their last known state.
- **Resonance Crystal** - strips the top tier off a pool and returns that catalyst intact. It is not consumed: the price is mana from the pool itself. Sneaking is required, so a stray click can't undo your progress.
- **Wardenia Belt** - a Wardenia's guard you wear in the Curios belt slot: hostile projectiles near you vanish, hostile mobs get pushed back, their explosions next to you fizzle out, and farmland survives your boots. Runs on mana from your rings and tablets.

A mined upgraded pool keeps its tier and its mana in the item, so you can move it without losing anything.

### On balance - why exactly x4

The exchange rate deliberately mirrors Mystical Agriculture's own ladder: 4 essences make 1 of the next tier, and the same in reverse. That isn't laziness, it's arithmetic - the Master Infusion Crystal has infinite durability, so climbing that ladder costs the player nothing. Any rate better than x4 becomes an infinite mana loop: buy cheap essence, craft it upward for free, sell it higher. At exactly x4 the loop pays nothing.

Buying back costs 25% more than selling, so the pool doesn't become free storage.

The flowers follow the same rule. Growing a crop with Mysticarnations always costs more mana than the Essentide gets back from its essence, so "a flower grows it, a flower eats it" never pays for itself.

### Configuration

Everything lives in `config/manaessencebridge-common.toml` and takes effect without rebuilding:

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

Every flower number - ranges, costs, mana per strike, note or bee - is in the `[flowers]` section.

This is a common config: single player uses your file, on a server the server's file wins - so players can't tune their own exchange rate. The x4 step itself is deliberately not configurable, for the reason above.

### Also included

- **A chapter of its own in the Lexica Botania** - every page of the mod in one place, including "An Essence Farm" and "An Advanced Essence Farm" with 3D layouts you can project into the world
- **Shared recipes** between Botania and Mystical Agriculture
- **Pools tinted by tier**, with matching particles on every conversion
- **Jade and The One Probe** support for pools, the condenser and every flower; without either, a built-in readout shows the pool
- **JEI tabs** for every exchange rate and for what the Essentide eats
- **Advancements** for every tier, the tools, the flowers and mana milestones
- **`/manabridge`** for operators: list a player's pools, set a pool's tier, change its owner
- **English and Russian** - fully localized
- Something special happens on October 17th

### For modpack makers: custom exchange rates

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

### Compatibility

This mod uses **no Mixins**. Everything goes through Botania's public API and ordinary Forge events - no third-party bytecode is touched, so it cannot add another Create-versus-Rubidium style conflict to your pack.

**Available for Minecraft 1.16.5, 1.18.2, 1.19.2 and 1.20.1**, all on Forge. Pick the file that matches your pack.

**Requires:** Botania and Mystical Agriculture

**Optional:** Mystical Agradditions (tier 6), JEI, Jade, The One Probe

### FAQ

**A creeper blew up my pool - where's my tier?**

Gone. Botania's Mana Pool carries a `survives_explosion` condition, so an exploded pool drops nothing at all, not even itself. Use a pickaxe - or plant a Wardenia next to it: it stops explosions in a 17x17 area.

**Can I apply a tier 5 catalyst straight away?**

No. Strictly in order, starting from tier 1.

**I run a public server. Can other players drain my pool?**

Turn on `privatePools`: then only the player who placed a pool's first catalyst can buy from it, strip it or tie a condenser to it. Operators are not limited, and owners can share all their pools with friends: `/manabridge trust add <player>`.

**Does it work with Botania addons?**

Yes, as long as their pool implements Botania's mana pool interface - `IManaPool` on 1.16.5 and 1.18.2, renamed to `ManaPool` on 1.19.2 and 1.20.1. The upgrade attaches to the interface, not to one specific block.
