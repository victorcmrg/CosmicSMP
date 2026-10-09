# ✦ CosmicSMP — Season 3

Paper plugin for **Cosmic SMP Season 3**: the **Galactic Merchant**, five **Stars** with skill trees
(1 passive + 3 primaries each), the **Brightness** economy, **stock** and **death bans**.

* **Target:** Paper **1.21.10** · Java **21** · Maven
* **Optional integrations:** ItemsAdder (items, blocks, sounds), MythicMobs (mobs, items), BetterModel (3D models)
  — all detected automatically at runtime; the plugin compiles and runs without them.

---

## Build

```bash
mvn clean package
# -> target/CosmicSMP-<version>.jar  (runs the automated tests first)
```

The test suite (`src/test`) boots the whole plugin on a simulated Paper 1.21.10 server (MockBukkit): default
files, star file restore, purchases, stock, unlocks, evaporation/rebuy and anti-dupe. Skip with `-DskipTests`.

When updating, **remove the old `CosmicSMP-*.jar`** from `plugins/` — the console prints the running version on start.

Drop the jar in `plugins/`, start the server once, then edit the generated files. Deleting a default file in
`stars/` resets it to the default on the next start / `/cosmic admin reload`.

---

## Gameplay (from the design document)

| Star | Passive (2 ✦) | Primary #1 | Primary #2 | Primary #3 |
|---|---|---|---|---|
| **Sonic** | Wardens are peaceful | Drag a player to you + Warden hit — **4 ✦** | Advanced Warden sonic beam — **3 ✦** | Space Warden summon, 5 hearts/hit — **5 ✦** |
| **Trail** | No fall damage | Alien Dragon fists, 4 hearts — **5 ✦** | Dragon's breath fireball, 6 hearts — **3 ✦** | Ride the Alien Dragon 10 s — **5 ✦** |
| **Elder** | Hits give Mining Fatigue 5 s | Laser, 3 hearts — **3 ✦** | Launch wave, 6 hearts — **5 ✦** | 5×5 force field 10 s, bounces intruders — **3 ✦** |
| **Cosmic** | Resistance II at night | Ride a comet 8 s — **3 ✦** | Clone a player's skin, name and trims 4 min — **5 ✦** | Phase the enemy out of their body (spirit returns by walking into / clicking the body) — **5 ✦** |
| **Nature** | +2 hearts near flowers | Vine, 6 hearts — **3 ✦** | Giant bee sting, 20 s nausea — **3 ✦** | Earth Clap walls, 7 hearts + stun — **5 ✦** |

Every star costs **6 ✦**. All numbers (costs, damage, ranges, durations, cooldowns, particles, sounds) live in
`stars/<id>.yml`.

**Brightness** — kill a player **+1**, killed by a player **−1**, capped at **7**. At **5**: permanent Speed I.
At **7**: 15-block Dash (**Sneak + F**). At **−3**: death ban. Buying anything spends brightness.

**Stars & stock** — a bought star goes to your inventory (soulbound). On death your stars **evaporate** and must be
re-bought. Each star has a global **stock**; the *first* purchase by a player uses one unit and permanently
*discovers* the star for them, so re-buying after death never uses stock and works even at 0 stock.

**Controls (holding a star)** — **Right-click** cast the selected primary · **F** cycle primaries
(or Sneak + Right-click, configurable). The action bar shows every primary in a fixed position with live cooldowns,
e.g. `① Sculk Grasp READY    ② 4.2s    ③    │    ✦ 5` (`messages.yml -> actionbar-hud`); the item itself shows
the native cooldown sweep.

**Astral Phase** — the target's body stays behind as a Mannequin with their skin and armour while the player becomes a
spirit (spectator: intangible, can't fight, interact or take damage; others see a soul wisp and a tether). Walking
into the body or clicking it returns them; after `max-duration-seconds` they are pulled back. A leash keeps the spirit
within `leash-radius`. Attacks on the empty body are stored (x1.5) and applied on return with kill credit; if they
would kill, the spirit is yanked back at once. Game mode and body position are persisted first, so quit, reload or a
crash always restore the player. Mobs hit by it become soulless (no AI) for a few seconds.

### Interpretation choices (all configurable)

| Topic | Default | Setting |
|---|---|---|
| "On death they lose one" | only deaths caused by a player cost brightness | `brightness.lose-on-any-death` |
| Kill farming | same victim gives no brightness for 10 min | `brightness.anti-farm` |
| Ability unlocks on death | lost with the star | `stars.reset-abilities-on-death` |
| Buying outside the merchant | not allowed (profile/tree = view + select) | `stars.purchases-require-merchant` |
| Death ban length | permanent until `/cosmic player unban` | `death-ban.duration-minutes` |
| Ability damage | `magic` (ignores armour, so "4 hearts" means 4 hearts) | `combat.damage-type` |
| Skill tree order | free | `stars.skill-tree-mode: SEQUENTIAL` |

---

## Commands

Everything lives under **`/cosmic`** (aliases `/cosmicsmp`, `/csmp`, `/cs`). Help is clickable with hover details,
every argument has tab completion, and players only ever see what they are allowed to run.

| Command | Permission | Default |
|---|---|---|
| `/cosmic help [category]` · `/cosmic info` | `cosmicsmp.command.help` | everyone |
| `/cosmic menu` — profile | `cosmicsmp.command.menu` | everyone |
| `/cosmic stars list · info <star> · tree <star> · select <1-3> · recover` | `cosmicsmp.command.stars` | everyone |
| `/cosmic brightness check · top` | `cosmicsmp.command.brightness(.top)` | everyone |
| `/cosmic brightness check <player>` | `cosmicsmp.command.brightness.others` | op |
| `/cosmic brightness set·give·take <player> <n>` | `cosmicsmp.admin.brightness` | op |
| `/cosmic merchant spawn [id] · remove <id\|nearest> · list · tp <id> · open [player]` | `cosmicsmp.admin.merchant` | op |
| `/cosmic stock view · set <star> <n\|unlimited> · reset [star\|all] · menu` | `cosmicsmp.admin.stock` | op |
| `/cosmic player info · givestar · takestar · unlock · clearcooldowns · reset · unban` | `cosmicsmp.admin.player` | op |
| `/cosmic admin reload · save · cleanup` / `debug` | `cosmicsmp.admin.reload` / `.debug` | op |

Permission groups: `cosmicsmp.player` (default **true**), `cosmicsmp.admin` (default **op**),
`cosmicsmp.*` (everything). Bypasses `cosmicsmp.bypass.cooldown` and `cosmicsmp.bypass.deathban` are never given by
default, not even to ops. `cosmicsmp.merchant.remote` lets someone buy from menus without a merchant.

---

## Files

```
plugins/CosmicSMP/
├─ config.yml          gameplay rules, perks, death ban, controls, merchant NPC, performance, hooks, modules
├─ messages.yml        every text: chat, titles, action bars, sounds, help, HUD, item lore formats
├─ stars/*.yml         one file per star (item, icon, passive, primaries, every ability setting)
├─ menus/*.yml         merchant, star_tree, confirm, profile, stock (all 9×6 chests)
└─ data/
   ├─ global.json      stock, merchant locations, death bans, leaderboard index
   └─ players/<uuid>.json
```

Text everywhere supports **MiniMessage** (`<gradient:#a855f7:#22d3ee>`, `<bold>`, `<hover>`, `<click>`),
**hex** (`&#a855f7`, `{#a855f7}`, `&x&a&8...`) and **legacy** codes (`&a`, `&l`). New keys added in updates are
merged into `config.yml`/`messages.yml` automatically, keeping your edits and comments.

### Menus

Every menu is a 54-slot chest. Each item can set `slot`/`slots` (ranges like `"9-17"`), `material`, `amount`, `name`,
`lore`, `custom-model-data`, `custom-model-data-strings`, `item-model`, `tooltip-style`, `glow`, `hide-tooltip`,
`skull-owner`, `skull-texture`, `color`, `enchantments`, `permission`, `click-sound` and `actions`
(`close`, `back`, `refresh`, `open:<menu>`, `command:<cmd>`, `console:<cmd>`, `message:<text>`, `sound:<sound>`).
Status texts, lore templates and connector panes of the dynamic parts are in the same files.

### Custom content (ItemsAdder / BetterModel / MythicMobs)

The vanilla visuals are placeholders; swapping them is pure configuration:

| What | Where | Example |
|---|---|---|
| Star items, menu icons | any `material:` | `material: itemsadder:cosmic:sonic_star` or `item-model: cosmic:sonic_star` |
| Display models (dragon fists, comet, fireball) | `settings.fist-item`, `comet-item`, `projectile-item` | `material: ia:cosmic:dragon_fist` |
| Summon / mount 3D models | `settings.models.<name>` | `models: {warden: space_warden}` (BetterModel id) |
| Summon / mount entity | `settings.mob` | `mob: mythic:SpaceWarden` |
| Merchant NPC | `config.yml -> merchant.entity / model` | `model: galactic_merchant` |
| Sounds | any sound field | `"cosmic:merchant.greet 1 1"` (ItemsAdder/resource-pack sounds are plain keys) |
| Particles | `settings.particles.*` | `{type: DUST, color: "#22d3ee", size: 1.4}` |

When a model/plugin is missing the plugin logs one warning and falls back to the vanilla placeholder.

---

## Reliability

* **Per-UUID JSON files** written atomically (`.tmp` + fsync + atomic move) with a `.bak` copy; a corrupt file falls
  back to the backup and is quarantined, never silently overwritten.
* Data is loaded on the async pre-login thread; purchases, brightness changes and bans are written within one tick;
  everything is flushed synchronously on shutdown.
* **Every timed value is an absolute timestamp** — cooldowns, bans, restock, disguises, flights — so restarts,
  crashes and reloads can't reset or extend anything.
* **Timed states survive crashes:** a player who disconnects mid-flight gets Slow Falling on the next join; a disguise
  resumes for its remaining time; a phased player is restored.
* **Nothing can be left behind:** summons, display models, holograms and merchant NPCs are non-persistent (never saved
  to the world) and tagged; any tagged entity that ever loads from disk is removed. Potion perks are short refreshed
  effects and health/jump modifiers are transient, so nothing sticks to a player if the plugin stops.
* **Soulbound stars:** can't be dropped, stored, put in bundles/frames/armor stands, crafted, smithed or placed;
  inventories are reconciled with the data on join/respawn, removing duplicates.

## Performance

* One shared tick loop drives every ability animation; periodic work is spread across ticks.
* Particles are sent only to players within `performance.particle-view-distance`, viewer lists are reused per frame,
  and ring/sphere geometry is pre-computed.
* Animations use **display entities with client-side interpolation** (one or two packets per animation instead of one
  per tick). Effects like Earth Clap never modify the world.
* The action-bar HUD only sends a packet when its text changes. Only online players stay in memory.
* Third-party hooks use cached reflection — no hard dependencies, no extra jars shaded.

---

## Architecture & expansion

```
com.cosmicsmp
├─ CosmicSMP                 bootstrap + service accessors
├─ api/                      CosmicAPI + events (BrightnessChange, StarPurchase, AbilityUnlock, AbilityCast, StarEvaporate)
├─ core/
│  ├─ command/               /cosmic → categories → sub commands, help, tab completion
│  ├─ config/                YamlFile (defaults merge), Settings (typed cache)
│  ├─ data/                  JsonStore, PlayerData(Manager), GlobalData(Manager)
│  ├─ fx/                    ParticleSpec, SoundSpec, Fx geometry, DisplayFx client animations
│  ├─ hook/                  ItemsAdder / MythicMobs / BetterModel bridges
│  ├─ item/                  ItemSpec (fully configurable items)
│  ├─ menu/                  YAML menu framework (GENERIC_9X6)
│  ├─ module/                CosmicModule + ModuleManager
│  ├─ task/                  EffectManager / TimedEffect, TempEntities
│  └─ text/                  Text (MiniMessage + hex + legacy), Messages, Placeholders
└─ feature/
   ├─ ability/               Ability / PassiveAbility contracts, combat, cooldowns, summons, timed states
   │  └─ impl/<star>/        the 20 Season 3 abilities
   ├─ brightness/            brightness, perks (speed, dash), death bans
   ├─ star/                  definitions, registry, purchase/unlock, star item, casting, HUD, stock
   ├─ merchant/              Galactic Merchant NPCs
   └─ menu/                  merchant, skill tree, confirm, profile, stock menus
```

**Add a star:** copy `stars/sonic.yml`, change `id`, texts and `ability:` ids, reload.

**Add an ability** (from another plugin with `depend: [CosmicSMP]`):

```java
public final class MeteorRain implements Ability {
    public String id() { return "meteor_rain"; }
    public CastResult cast(AbilityContext ctx) {
        double damage = ctx.def().num("damage", 8);   // read from the star file's settings
        // ... start a TimedEffect for the animation
        return CastResult.SUCCESS;                   // cooldown starts
    }
}
CosmicAPI.registerAbility(new MeteorRain());          // then: ability: meteor_rain in any stars/*.yml
```

Also available: `CosmicAPI.registerPassive`, `registerModule`, `registerCommands`, `getBrightness`,
`addBrightness`, `ownsStar`, `hasPassive`.
