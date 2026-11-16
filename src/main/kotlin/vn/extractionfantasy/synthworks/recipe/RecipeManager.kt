package vn.extractionfantasy.synthworks.recipe

import org.bukkit.inventory.ItemStack
import org.bukkit.configuration.file.YamlConfiguration
import vn.extractionfantasy.synthworks.SynthworksPlugin
import java.io.IOException
import java.util.Locale

class RecipeManager(private val plugin: SynthworksPlugin) {
    private val recipes = linkedMapOf<String, SynthworksRecipe>()

    fun reload() {
        recipes.clear()
        val recipeFiles = linkedMapOf<String, java.io.File>()
        val directory = plugin.dataFolder.resolve("recipes")
        if (directory.isDirectory) {
            directory.walkTopDown()
                .filter { it.isFile && it.extension.equals("yml", true) }
                .sortedBy { it.path }
                .forEach { file ->
                    val id = file.nameWithoutExtension.lowercase(Locale.ROOT)
                    if (recipeFiles.putIfAbsent(id, file) != null) {
                        plugin.logger.warning("Duplicate recipe ID '$id' found at '${file.path}'.")
                    }
                }
        }

        val legacyFile = plugin.dataFolder.resolve("recipes.yml")
        val legacyYaml = YamlConfiguration.loadConfiguration(legacyFile)
        val legacyRoot = legacyYaml.getConfigurationSection("recipes")
        legacyRoot?.getKeys(false)?.forEach { rawId ->
            val id = rawId.lowercase(Locale.ROOT)
            if (!id.matches(Regex("[a-z0-9_-]+"))) {
                plugin.logger.warning("Skipping legacy recipe '$rawId': invalid ID.")
                return@forEach
            }
            val section = legacyRoot.getConfigurationSection(rawId) ?: return@forEach
            if (id !in recipeFiles) {
                val migrated = sectionToYaml(section)
                val configuredCategory = migrated.getString("category")?.lowercase(Locale.ROOT)
                val category = if (configuredCategory.isNullOrBlank() || configuredCategory == "misc") {
                    id.substringBefore('_').takeIf { '_' in id && it.matches(Regex("[a-z0-9-]+")) } ?: "misc"
                } else configuredCategory.takeIf { it.matches(Regex("[a-z0-9_-]+")) } ?: "misc"
                val target = recipeFile(category, id)
                if (!target.parentFile.exists() && !target.parentFile.mkdirs()) {
                    plugin.logger.severe("Could not create recipe folder for '$id'.")
                    return@forEach
                }
                try {
                    migrated.save(target)
                    recipeFiles[id] = target
                    legacyYaml.set("recipes.$rawId", null)
                } catch (exception: IOException) {
                    plugin.logger.severe("Could not migrate recipe '$id': ${exception.message}")
                }
            } else {
                legacyYaml.set("recipes.$rawId", null)
            }
        }
        if (legacyRoot != null && legacyYaml.getConfigurationSection("recipes")?.getKeys(false).isNullOrEmpty()) {
            legacyYaml.set("recipes", null)
        }
        if (legacyFile.isFile && legacyYaml.getConfigurationSection("recipes") == null) {
            try {
                legacyYaml.save(legacyFile)
            } catch (exception: IOException) {
                plugin.logger.severe("Could not finish migrating recipes.yml: ${exception.message}")
            }
        }

        for ((id, file) in recipeFiles) {
            val section = YamlConfiguration.loadConfiguration(file)
            loadRecipe(id, section)
        }
    }

    private fun loadRecipe(rawId: String, section: YamlConfiguration) {
            val id = rawId.lowercase(Locale.ROOT)
            val output = section.getString("result")?.lowercase(Locale.ROOT)
            if (!id.matches(Regex("[a-z0-9_-]+")) || output.isNullOrBlank()) {
                plugin.logger.warning("Skipping recipe '$rawId': invalid ID or missing result.")
                return
            }
            if (plugin.itemManager.definition(output) == null) {
                plugin.logger.warning("Skipping recipe '$rawId': unknown result item '$output'.")
                return
            }

            val ingredients = (0..7).mapNotNull { slot ->
                section.getItemStack("ingredients.$slot")
                    ?.takeUnless { it.type.isAir || it.amount < 1 }
                    ?.clone()
            }
            if (ingredients.isEmpty()) {
                plugin.logger.warning("Skipping recipe '$rawId': it has no ingredients.")
                return
            }

            val configuredCategory = section.getString("category")?.lowercase(Locale.ROOT)
            val category = if (configuredCategory.isNullOrBlank() || configuredCategory == "misc") {
                id.substringBefore('_').takeIf { '_' in id && it.matches(Regex("[a-z0-9-]+")) } ?: "misc"
            } else configuredCategory

            recipes[id] = SynthworksRecipe(
                id = id,
                category = category,
                resultItemId = output,
                ingredients = ingredients,
                requiresBlueprint = section.getBoolean("requires-blueprint", false)
            )
    }

    private fun sectionToYaml(section: org.bukkit.configuration.ConfigurationSection): YamlConfiguration =
        YamlConfiguration().also { yaml ->
            section.getKeys(true).filterNot { section.isConfigurationSection(it) }.forEach { key ->
                yaml.set(key, section.get(key))
            }
        }

    private fun recipeFile(category: String, id: String): java.io.File {
        require(category.matches(Regex("[a-z0-9_-]+"))) { "Invalid recipe category: $category" }
        require(id.matches(Regex("[a-z0-9_-]+"))) { "Invalid recipe ID: $id" }
        return plugin.dataFolder.resolve("recipes").resolve(category).resolve("$id.yml")
    }

    fun all(): List<SynthworksRecipe> = recipes.values.toList()

    fun recipe(id: String): SynthworksRecipe? = recipes[id.lowercase(Locale.ROOT)]

    fun categories(): List<String> = recipes.values.map { it.category }.distinct().sorted()

    fun inCategory(category: String): List<SynthworksRecipe> =
        recipes.values.filter { it.category.equals(category, ignoreCase = true) }

    @Throws(IOException::class)
    fun save(recipe: SynthworksRecipe) {
        val id = recipe.id.lowercase(Locale.ROOT)
        val category = recipe.category.lowercase(Locale.ROOT)
        val file = recipeFile(category, id)
        if (!file.parentFile.exists() && !file.parentFile.mkdirs()) {
            throw IOException("Could not create recipe folder '${file.parentFile}'.")
        }
        val yaml = YamlConfiguration()
        yaml.set("category", category)
        yaml.set("result", recipe.resultItemId)
        yaml.set("requires-blueprint", recipe.requiresBlueprint)
        for (slot in 0..7) {
            yaml.set("ingredients.$slot", recipe.ingredients.getOrNull(slot))
        }
        yaml.save(file)
        val previous = recipes[id]
        if (previous != null && !previous.category.equals(category, true)) {
            val oldFile = recipeFile(previous.category.lowercase(Locale.ROOT), id)
            if (oldFile.exists() && !oldFile.delete()) {
                throw IOException("Could not remove previous recipe file '$oldFile'.")
            }
        }
        val legacyFile = plugin.dataFolder.resolve("recipes.yml")
        if (legacyFile.isFile) {
            val legacyYaml = YamlConfiguration.loadConfiguration(legacyFile)
            legacyYaml.set("recipes.$id", null)
            legacyYaml.save(legacyFile)
        }
        recipes[id] = recipe.copy(id = id, category = category)
    }

    @Throws(IOException::class)
    fun delete(id: String) {
        val cleanId = id.lowercase(Locale.ROOT)
        val existing = recipes[cleanId]
        if (existing != null) {
            val file = recipeFile(existing.category.lowercase(Locale.ROOT), cleanId)
            if (file.exists() && !file.delete()) throw IOException("Could not delete recipe file '$file'.")
        }
        val legacyFile = plugin.dataFolder.resolve("recipes.yml")
        if (legacyFile.isFile) {
            val yaml = YamlConfiguration.loadConfiguration(legacyFile)
            yaml.set("recipes.$cleanId", null)
            yaml.save(legacyFile)
        }
        recipes.remove(cleanId)
    }
}
