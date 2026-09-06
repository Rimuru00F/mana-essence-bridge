## Mana Essence Bridge

**A bridge between Botania mana and Mystical Agriculture essence.**

Your Inferium farm runs around the clock while your mana pools sit half empty? Or the other way round — endoflames burning fuel into nothing while you're short on essence? This addon merges the two economies: an upgraded Mana Pool becomes a two-way exchange.

No new blocks. The mod extends the Mana Pool you already have.

### How it works

Craft an **Inferium Mana Catalyst** and right-click it onto a plain Mana Pool. The pool is now upgraded and can:

| Action | Result |
| --- | --- |
| Right-click with essence | convert one |
| Shift + right-click with essence | convert the whole stack |
| Shift + right-click, empty hand | buy essence back with mana |
| Hopper or pipe into the pool | the same, automated |

### Five tiers

Each catalyst unlocks the next essence level and demands its own rung of Botania progression:

| Tier | Essence | Mana given | Mana to buy | Crafted at |
| --- | --- | --- | --- | --- |
| 1 | Inferium | 2,000 | 2,500 | Crafting table |
| 2 | Prudentium | 8,000 | 10,000 | Petal Apothecary |
| 3 | Tertium | 32,000 | 40,000 | Runic Altar |
| 4 | Imperium | 128,000 | 160,000 | Elven Trade |
| 5 | Supremium | 512,000 | 640,000 | Terra Plate |

No skipping ahead: catalysts apply strictly in order, and the higher tiers are physically out of reach until you have smelted Terrasteel, opened the Alfheim portal and beaten the Gaia Guardian. Until a rung is cleared, its entry stays hidden in the Lexica Botania and its catalyst stays hidden in JEI.

### On balance - why exactly x4

The exchange rate deliberately mirrors Mystical Agriculture's own ladder: 4 essences make 1 of the next tier, and the same in reverse. That isn't laziness, it's arithmetic — the Master Infusion Crystal has infinite durability, so climbing that ladder costs the player nothing. Any rate better than x4 becomes an infinite mana loop: buy cheap essence, craft it upward for free, sell it higher. At exactly x4 the loop pays nothing.

Buying back costs 25% more than selling, so the pool doesn't become free storage.

### Configuration

Everything lives in `config/manaessencebridge-common.toml` and takes effect without rebuilding:

| Option | Default | What it does |
| --- | --- | --- |
| `manaPerInferium` | 2000 | mana per Inferium Essence; every tier above is worth 4x more |
| `buyMarkupPercent` | 125 | buy-back price as a percent of the sell price |
| `maxTier` | 5 | highest tier the mod will handle |
| `buyHighestTier` | true | empty-hand buying: the best tier you can afford, or always Inferium |
| `automationEnabled` | true | accept essence from hoppers and pipes |
| `hideLockedInJei` | true | hide catalysts for gates you haven't passed |
| `showHud` | true | on-screen readout when looking at a pool |

This is a common config: single player uses your file, on a server the server's file wins — so players can't tune their own exchange rate.

The x4 step itself is deliberately not configurable, for the reason above.

### Also included

- **Pools are tinted by tier** — one glance tells you what's where
- **On-screen readout** — look at a pool to see its tier, rate and stored mana
- **Upgrades survive relocation** — break the pool, carry it, place it again: the tier is still there
- **JEI tab** showing every exchange rate
- **Lexica Botania entries**, filed alongside the rest of Botania
- **Advancements** — a chain of six, one per tier
- **English and Russian** — fully localized, chat messages included

### Compatibility

This mod uses **no Mixins**. Everything goes through Botania's public API and ordinary Forge events — no third-party bytecode is touched, so it cannot add another Create-versus-Rubidium style conflict to your pack.

**Available for Minecraft 1.16.5 and 1.20.1**, both on Forge. Pick the file that matches your pack.

**Requires:** Botania and Mystical Agriculture

**Optional:** JEI

### FAQ

**A creeper blew up my pool - where's my tier?**

Gone. Botania's Mana Pool carries a `survives_explosion` condition, so an exploded pool drops nothing at all, not even itself. Use a pickaxe.

**Can I apply a tier 5 catalyst straight away?**

No. Strictly in order, starting from tier 1.

**Does it work with Botania addons?**

Yes, as long as their pool implements `IManaPool` — the upgrade attaches to the interface, not to one specific block.
