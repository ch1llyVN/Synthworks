# Patch notes

## Synthworks 0.1.6

### Changed

- Crafter particles now rise vertically from their configured emission point, matching the upward effect of Trial Spawners.
- Added configurable particle position (`particle-location.x/y/z`), spread (`particle-delta.x/y/z`), and upward speed; particle count remains configurable.

## Synthworks 0.1.5

### Added

- Made the Crafter particle configurable with `settings.crafter.particle`; the default is `trial_spawner_detection_ominous`.
- Added Quick Craft using ingredients from the player's inventory.
- Persisted the first manual craft after a blueprint unlock; blueprint recipes can use Quick Craft after that first manual craft.
- Moved custom items and recipes into per-category folders with a separate YAML file for each entry, migrating entries from the former aggregate files.

### Changed

- Reworked the recipe editor to follow the player's crafting layout, with ingredient previews, matching input positions, and centered output.
- Blueprint names now use the output item's display name rather than its recipe/item ID.
- Craft GUI controls now follow `B` (Back), `Q` (Quick Craft), and `C` (Manual Craft).

## Synthworks 0.1.4

### Changed

- Renamed the plugin and output JAR to Synthworks; the root command is now `/synthworks` with `/sw` as its short alias.
- Renamed the Kotlin entry point and source package to Synthworks.
- Rebranded player/admin menus, messages, item labels, permissions, configuration, and documentation.
- Replaced the placeable Smithing Table workstation with a Crafter block and changed the default menu table material to `CRAFTER`.

### Compatibility

- Copies missing files from the old `plugins/Synthesis/` directory into `plugins/Synthworks/` without overwriting existing files or deleting the old directory.
- Reads old `synthesis` item/blueprint markers and existing Synthesizer location data.
- Recognizes the former permissions `synthesis.admin` and `synthesis.use`.

## Legacy Synthesis 0.1.3 (before the Synthworks rename)

### Added

- Added `/synth` and `/syn` root aliases.
- Organized commands under `open`, `give`, `editor`, `reload`, and `info` groups.
- Added admin command to give a recipe blueprint directly.
- Retained legacy command forms for compatibility.
- Previously proposed **Synthesis: Extraction Fantasy** as the display name; replaced by Synthworks in 0.1.4.

## Legacy Synthesis 0.1.1 (before the Synthworks rename)

### Added

- Added the placeable Synthesizer workstation, based on the Smithing Table model, with nearby enchantment particles and persisted block locations.
- Added `/synthesize givesynth <player>` for administrators.
- Added category-first navigation for player recipes and administrator item/recipe lists.
- Expanded recipe ingredient capacity from five to eight slots.
- Changed blueprints to use the Knowledge Book item.
- Added full Paper `ItemStack` byte serialization for custom item templates so item metadata, components, and persistent data are retained.
- Added item giving from the item management detail menu.
- Added gray glass-pane menu fillers, green confirmation controls, red cancel/back controls, and a yellow custom-item template slot.

### Fixed

- Included Kotlin Standard Library in the plugin JAR to prevent `NoClassDefFoundError` during plugin startup.
- Improved item template movement in the editor and recipe category selection/creation chat flow.
- Resolved recipe categories from the ID prefix when older recipes still have their category set to `misc`.
