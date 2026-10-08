## 1.20.1.4

The biggest update so far: bigger pools, two new blocks, eight new Botania flowers, four new tools, a sixth tier for Mystical Agradditions players and a chapter of its own in the Lexica Botania.

### Pools

- **Upgraded pools hold more mana.** Capacity doubles with every tier above the first: a plain Mana Pool holds 2 million at tier 2 and 16 million at tier 5. Diluted and Fabulous pools scale from their own size. Config: `boostCapacity` (on by default). A pool too small for a tier's essence can't take that catalyst, instead of wasting it.
- **Tier 6 - Insanium** *(only with Mystical Agradditions installed)*. The Insanium Mana Catalyst is made on the Runic Altar from a Supremium catalyst, 4 Insanium Essence, 2 Gaia Spirit Ingots and the Runes of Pride and Envy, for 1,000,000 mana - and it can only be applied after defeating the **Gaia Guardian II**. A tier-6 pool holds 32 times its normal mana and trades Insanium at 2,048,000 mana each. Without Agradditions, tier 6 doesn't show up anywhere. Config: `enableInsanium`.
- **Upgraded pools glow** with faint sparks in their tier's colour, so the tier shows from afar. Client-side; config `poolGlow`.
- **Pool network** - bind an Essence Mirror to one upgraded pool, then Shift + right-click another with it to link them. Once a second, mana flows from the fuller pool to the emptier one until both are equally full, however far apart they are (same dimension, both loaded; redstone pauses a link). Up to eight links per pool. Config: `poolLinkRate`.
- **Shared pools** - with `privatePools` on, give friends access to all your pools with `/manabridge trust add <player>` (`remove`, `list`).
- **Private pools** for public servers: with `privatePools = true`, only a pool's owner (whoever placed its first catalyst) can buy essence from it, remove its tier, switch its siphon or tie a condenser to it. Operators are not limited. Off by default.
- **Expander Crystals** (new block): placed against an upgraded pool, each crystal adds 25% of its capacity; crystals touching those count too, up to 8 per pool. Breaking one never loses mana - the pool shrinks only as the mana is spent. Config: `expanderPercent`, `expanderMaxCrystals`.
- **A mined upgraded pool keeps its mana.** It travels in the item (the tooltip shows how much) and is back when you place the pool. Config: `keepManaOnBreak`.
- Pools upgraded before an owner was recorded get one on the next manual action, so their automation counts for advancements too.

### Essence Condenser (new block)

The pool's buy-back, automated: the condenser draws mana from a pool and turns it into essence.

- Right-click with an essence to choose the tier; it works with an upgraded pool touching it, or one up to 12 blocks away bound with the Wand of the Forest.
- A **reserve** keeps 0, 25, 50 or 75% of the pool untouched, or **90% - surplus mode**, which only uses mana the pool is about to have no room for. Change it with Shift + right-click on an empty hand, or a plain right-click with the wand.
- Takes one essence every 2 ticks (config `condenserIntervalTicks`), so the pool drains evenly instead of in gulps.
- The essence floats above the block, and the condenser pushes it into an adjacent chest, hopper or pipe on its own.
- A redstone signal pauses it; a comparator reads how full it is; Jade, The One Probe and the Wand of the Forest HUD show what it's doing.

### New items

- **Essence Mirror** - bind it to an upgraded pool, then trade with that pool from anywhere in its dimension. Right-click opens the pool's window: its mana, the prices, and buy or sell buttons for every essence tier - pick how many (1 to 64) with the arrows or the mouse wheel, or send everything at once. Shift + right-click sends all the essence in your inventory into the pool. Same prices as the pool itself.
- **Mana Ledger** - right-click to open a window with every upgraded pool you own: tier, coordinates, distance and a direction arrow, a mana bar, throughput and auto-pull. It also shows how fast each pool is filling or draining (mana per minute) and which condenser is drawing from it - and its pool-network links: hover a pool to light up the pools it is linked to. Sort by distance, tier or mana; pools in unloaded chunks show their last known state.
- **Mana Fertilizer** - Fertilized Essence steeped in a Mana Pool (45,000 mana). Right-click a Mystical Agriculture crop and every crop in the 3x3 around it grows two stages; works from a dispenser too. Priced so it never beats the crops' essence value. Config: `manaFertilizerStages`. Four of them on the Terrestrial Agglomeration Plate with the Runes of Spring and Earth make a **Greater Mana Fertilizer**, which ripens every crop in a 5x5 at once.
- **Condensation Lens** - for a Mana Spreader aimed at an upgraded pool: bursts turn straight into Inferium at the buy-back price instead of filling the pool. It also keeps a full pool from blocking the spreader.
- **Wardenia Belt** - a Curios belt that carries a Wardenia's guard with you: it snuffs out projectiles hostile mobs shoot within 4 blocks, pushes hostile mobs closer than 3 blocks away (bosses stay put), defuses their explosions within 6 blocks (creepers, ghast fireballs - your own TNT still works) and keeps you from trampling farmland. Each action takes mana from your mana rings and tablets, at the Wardenia's prices. Made on the Runic Altar from two Wardenias. Curios is already required by Botania, so nothing new to install.

### New flowers

Functional (spend mana):

- **Mysticarnation** - an Agricarnation for Mystical Agriculture crops. Growing a crop this way always costs more mana than its essence is worth, and no more than 3 Mysticarnations speed up the same crop.
- **Reaperbloom** - harvests ripe Mystical Agriculture crops in an 11x11 area and replants them.
- **Wardenia** - guards any farm in a 9x9 area: keeps farmland moist, stops it being trampled, pushes hostile mobs out, stops their arrows, tridents, fireballs and potions, and snuffs out explosions - creepers, TNT, fireballs - in a twice larger 17x17 area. Blasts next to that area leave it untouched.

Generating (make mana):

- **Essentide** - eats Mystical Agriculture resource essences; the higher the crop tier, the more mana. A JEI tab lists what it eats and for how much.
- **Brookbell** - mana from items drifting past in flowing water, without taking them. For any water-based farm.
- **Boltbloom** - catches lightning: every strike nearby gives a big burst of mana, and in a thunderstorm it calls harmless lightning down onto itself.
- **Melodia** - listens to note blocks; varied tunes and chords pay more than one note on repeat.
- **Bumblebloom** - bees pollinate it like any flower, and each visit makes mana.

Every flower also has a **floating** version on a Botania island, crafted like Botania's own: the flower plus any floating flower. A floating Wardenia guards 16 blocks down, so it can hang over a house or a farm. They spin and bob like Botania's (config `animateFloatingFlowers`), and the **Manaseer Monocle** now shows the radius of every flower from this mod. Jade and The One Probe show what each flower is doing - digesting, resting, waiting for lightning, mana made in the last second. All flower numbers are in the new `[flowers]` config section.

### Also new

- **A chapter of its own in the Lexica Botania** - every page of the mod in one place, plus "An Essence Farm" and "An Advanced Essence Farm" - floating flowers over a full 9x9 field - both with a 3D layout you can project into the world, and "Shared Recipes".
- **Shared recipes** between Botania and Mystical Agriculture: nether quartz into a Prosperity Shard by mana infusion, bone meal into Fertilized Essence with an Alchemy Catalyst, and an Infusion Crystal on the Runic Altar.
- **New advancements** for the siphon, the condenser, binding it with the wand, the flower farm, all eight flowers, the ledger, the lens, the mirror, a full circle of 1 million mana sold and bought back, a pool with 16 million mana, and tier 6.
- **`/manabridge`** for operators: list a player's pools, set a pool's tier, change its owner.
- Something special happens on October 17th.

### For modpack makers

- **Custom exchange rates by datapack**: any item or tag can be sold into an upgraded pool for a set amount of mana, from a set pool tier - `data/<namespace>/manaessencebridge_exchange/*.json`. Sell-only, accepted by hand, hoppers, the Siphon and the Mirror, and shown in the item tooltip. See the mod page for the format.

### Notes

- **Update both client and server.** The network format changed; a mismatched pair refuses to connect with a clear version message.
- Existing worlds are safe: pools keep their tiers and grow to their new capacity the next time a player sees them.
