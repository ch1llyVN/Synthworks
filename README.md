# Synthworks v1.0.0

Kotlin/Paper plugin for Extraction Fantasy on Purpur 1.21.11. Synthworks provides configurable custom items, recipe-book and blueprint GUIs, custom crafting, and an in-game administrator editor.

For the complete Vietnamese usage, configuration, migration, and implementation guide, see [SYNTHWORKS-DOCUMENTATION.md](./SYNTHWORKS-DOCUMENTATION.md).

## Requirements and build

- Java 21
- Gradle 8.10+
- Purpur/Paper 1.21.11

```bash
gradle build
```

The plugin JAR is created at `build/libs/Synthworks-1.0.0.jar`. Copy it to the server's `plugins/` directory and restart. Kotlin Standard Library is bundled into the JAR.

When upgrading from Synthesis, Synthworks copies files that are missing from `plugins/Synthworks/` from the old `plugins/Synthesis/` folder without overwriting existing files. The old folder is left untouched.

## Commands

- Root command and alias: `/synthworks`, `/sw`
- `/synthworks open <player>` — open the player menu for an online player (admin)
- `/synthworks editor` — open the administrator editor (`/synthworks edit` also works)
- `/synthworks give item <player> <id> [amount]` — give a configured item
- `/synthworks give crafter <player>` — give a placeable Synthworks Crafter
- `/synthworks give blueprint <player> <recipe_id>` — give a recipe blueprint
- `/synthworks reload` — reload config, items, and recipes
- `/synthworks info` — show plugin status (`/synthworks debug` is retained)

The root command is also registered as `/sw`. Admin permission: `synthworks.admin` (default OP). Player menu permission: `synthworks.use` (default true). The legacy `synthesis.admin` and `synthesis.use` permissions remain recognized during migration.

## Player crafting

Right-click a placed Synthworks Crafter to open the player menu. Ordinary vanilla Crafter blocks are not intercepted and retain their normal redstone crafting behavior. The Recipe Book is grouped by recipe category. Recipes marked as requiring a blueprint remain locked until the player right-clicks the matching blueprint. Unlocked recipes also appear in Blueprint Storage.

Select a recipe and either place each required ingredient in its matching input slot and press **C** for manual crafting, or press the cyan **Q** panel to quick craft once from your inventory. Quick Craft is available immediately for recipes without a blueprint. For blueprint recipes, unlock the blueprint and successfully craft manually once; Quick Craft is enabled for later crafts. Missing ingredients are not consumed.

Blueprint unlocks are stored per player under `plugins/Synthworks/players/`.

Crafter particles rise from the block like the Trial Spawner effect. In `config.yml`, `settings.crafter.particle-location` sets the emission point relative to the block origin (for example `x: 1, y: 1, z: 1` is one block offset on each axis); `particle-delta` sets random spread around that point, `particle-count` sets particles per emission, and `particle-speed` sets their upward velocity. The default particle is `trial_spawner_detection_ominous`.

## Administrator editor

Run `/synthworks editor` as an operator. The editor can create, edit, and delete custom items and recipes, and give recipe outputs or blueprints.

- Custom item creation: place a template item in the center slot. The menu can set display name, lore, block placement, vanilla-crafting eligibility, glint, unbreakable state, durability, max stack size, and attributes. Text options are entered in chat. Attribute format: `ATTRIBUTE:amount:operation,...`, for example `ARMOR:4:ADD_NUMBER`.
- Recipe creation: place up to eight ingredient stacks in the ingredient slots and a Synthworks custom item in the output slot. Ingredient stack amounts are saved as required counts. Set the category and whether a blueprint is required, then save.
- Recipe categories create the folders shown in the player Recipe Book (for example `dtier`, `ctier`, `junk`, `salvage`, `refined`, `misc`).

Item definitions are stored as `plugins/Synthworks/custom_items/<folder>/<item_id>.yml`; recipes are stored as `plugins/Synthworks/recipes/<folder>/<recipe_id>.yml`. Existing single-file YAML data is split into this folder structure during reload.

## Item configuration

Example `custom_items/ctier/ctier_copper_wire.yml`:

```yaml
material: COPPER_INGOT
name: "&6Copper Wire"
lore:
  - "&7Industrial component"
custom-model-data: 43001
amount: 1
max-stack-size: 64
unbreakable: false
glint: false
block-placement: false
vanilla-crafting: false
durability: 0
attributes: {}
```

Run `/synthworks reload`, then `/synthworks give item <player> ctier_copper_wire`.

Synthworks writes new PersistentDataContainer keys under its `synthworks` namespace and still reads the old `synthesis` keys, so items and blueprints already distributed remain recognized. ExecutableItems integration is not included in this version.

## Notes

- Attribute enum identifiers depend on the Bukkit API version.
- `durability` is current damage, not maximum durability.
- Use unique item IDs and avoid renaming IDs after distribution unless planning a migration.
