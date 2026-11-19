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
| Soul Rupture: hold **Right-Click** | Blade is raised and charges (spiralling prismatic energy, rising beacon pitch). Empowered at once; each melee hit costs 250 souls and calls a sky beam down onto the mob |
| Reality Cut: hold **Right-Click** ~1 s | Charged (beacon activate). Each hit costs 250 souls, applies Soul Sever (10 s) and slices a rift |
| Reality Cut: **Sneak + Right-Click** | Cycle the rift's target: Overworld / Nether / End |
| ANNIHILATION: **Right-Click** (>= 100 souls) | Spends all souls, brief casting lock; an orbital beam streaks down from Y=320 onto the block you look at (<= 64 blocks) |

Souls: +1 per mob killed in melee, +50 per boss, **capped at 100,000**. Bonus damage = `souls / 100,000 * 35` (+35.0 at the cap).

## Design notes
* Holding right-click is the vanilla item-use state, so the raised-blade pose is visible to everyone (third person: arm raised,
  first person: `applyForgeHandTransform`). Vanilla swallows left-clicks during item use, so the client forwards them
  as `SwordStrikePayload`; the server checks reach and performs the attack.
* Orbital beam: the tip accelerates from the sky ceiling to the surface in 14 ticks (Rupture: 5). On impact the sonic boom,
  dragon growl and explosion play, then the tip bores to the crater floor over 25-40 ticks while a fullbright translucent
  dome (`entityTranslucentEmissive`) travels down with it. Entities inside the dome are vaporised, survivors out to 3x the
  radius get Soul Sever, and no fog/clouds are left behind.
* Crater: radius ~15 (100 souls) -> 120 (100,000 souls); depth reaches the bedrock layer at the cap. `CraterManager` removes
  blocks behind the tip (paraboloid bowl, centre outwards) inside a 14 ms/tick budget and only in loaded chunks, so it never
  freezes the server; large craters finish a few seconds after the beam. Bedrock and other indestructible blocks are skipped.
  Queued craters are not saved across a server restart. The caster gets Resistance + Slow Falling if inside the radius.
* Rift rendering: stencil mask -> other dimension sky (dome / End cube / sun / stars) -> stencil clear -> prismatic rim.
  Requires a stencil-capable main framebuffer (`RenderTarget#enableStencil()` is called on client setup).
  If a rift already points at the current dimension it folds entities 64 blocks forward instead.
* Not compiled in the authoring environment (no network): if `./gradlew build` reports an API mismatch it will be a
  one-line signature fix (candidates: `Item#getUseDuration`, `Gui.leftHeight`, `RenderTarget#enableStencil`, `Entity#teleportTo`).
