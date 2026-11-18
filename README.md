# The Soul Sword (Blade of Infinite Strength) - `soul_sword`

NeoForge 1.21.1 (21.1.x), Java 21.

## Build / run
    ./gradlew build          # jar in build/libs/
    ./gradlew runClient      # dev client
Needs internet on first run (NeoForge + Minecraft are downloaded by the ModDevGradle plugin).

## Recipe
Netherite ingot / 2 diamond blocks / nether star (shaped, see `data/soul_sword/recipe/soul_sword.json`), or `/give @s soul_sword:soul_sword`.

## Controls
| Input | Effect |
|---|---|
| **X** | Cycle Soul Rupture -> Reality Cut -> ANNIHILATION (action bar shows the mode) |
| Soul Rupture: hold **Right-Click** | Empowered. Each melee hit on a mob costs 250 souls and drops a sky-to-ground soul beam + shockwave on it |
| Reality Cut: hold **Right-Click** ~1 s | Charged (beacon sound). Each hit costs 250 souls, applies Soul Sever (10 s) and slices a rift |
| Reality Cut: **Sneak + Right-Click** | Cycle the rift's target: Overworld / Nether / End |
| ANNIHILATION: **Right-Click** (>= 100 souls) | Spends all souls, brief casting lock, beam hits the block you look at (<= 64 blocks) |

Souls: +1 per mob killed in melee, +50 per boss. Bonus damage = `souls / 40000` (1,000,000 souls = +25), uncapped.

## Design notes
* Attacking is impossible while a vanilla "use item" is active, so Rupture/Reality Cut "hold right-click" is implemented
  as a short refresh window renewed by the repeating `use()` call (the sword is never in the vanilla using state in those modes).
* Annihilation crater: radius ~14 (100 souls) -> 180 (1,000,000 souls) -> up to 256; depth reaches the bedrock layer at 1,000,000 souls.
  Blocks are removed by `CraterManager` in a 12 ms/tick budget, centre outwards, chunk by chunk (bedrock/indestructibles are skipped).
  Queued craters are not saved across a server restart. The caster gets Resistance + Slow Falling if inside the radius.
* Rift rendering: stencil mask -> other dimension sky (dome / End cube / sun / stars) -> stencil clear -> prismatic rim.
  Requires a stencil-capable main framebuffer (`RenderTarget#enableStencil()` is called on client setup).
  The window shows the linked dimension's *sky/environment*, not its terrain. If the rift already points at the current
  dimension it folds entities 64 blocks forward instead.
