package vn.extractionfantasy.synthworks.blueprint

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.configuration.file.YamlConfiguration
import vn.extractionfantasy.synthworks.SynthworksPlugin
import vn.extractionfantasy.synthworks.recipe.SynthworksRecipe
import java.io.IOException
import java.util.UUID

class BlueprintManager(private val plugin: SynthworksPlugin) {
    private val unlockedByPlayer = mutableMapOf<UUID, MutableSet<String>>()

    fun create(recipe: SynthworksRecipe): ItemStack {
        val stack = ItemStack(Material.KNOWLEDGE_BOOK)
        val meta = stack.itemMeta
        val itemName = plugin.itemManager.definition(recipe.resultItemId)?.name ?: recipe.resultItemId
        meta.displayName(plugin.itemManager.legacy.deserialize("&bBlueprint: &f$itemName"))
        meta.lore(listOf(plugin.itemManager.legacy.deserialize("&7Right-click to unlock this recipe.")))
        plugin.config.getInt("settings.blueprint.custom-model-data").takeIf { it > 0 }?.let(meta::setCustomModelData)
        meta.persistentDataContainer.set(plugin.blueprintKey, PersistentDataType.STRING, recipe.id)
        stack.itemMeta = meta
        return stack
    }

    fun recipeId(stack: ItemStack?): String? {
        if (stack == null || stack.type.isAir || !stack.hasItemMeta()) return null
        val data = stack.itemMeta.persistentDataContainer
        return data.get(plugin.blueprintKey, PersistentDataType.STRING)
            ?: data.get(plugin.legacyBlueprintKey, PersistentDataType.STRING)
    }

    fun isUnlocked(player: Player, recipeId: String): Boolean = recipeId in unlocked(player)

    fun canQuickCraft(player: Player, recipe: SynthworksRecipe): Boolean =
        !recipe.requiresBlueprint ||
            (isUnlocked(player, recipe.id) && recipe.id in playerData(player.uniqueId).getStringList("manual-crafts"))

    @Throws(IOException::class)
    fun recordManualCraft(player: Player, recipe: SynthworksRecipe) {
        if (!recipe.requiresBlueprint) return
        val file = playerFile(player.uniqueId)
        if (!file.parentFile.exists() && !file.parentFile.mkdirs()) {
            throw IOException("Could not create player data directory.")
        }
        val data = YamlConfiguration.loadConfiguration(file)
        val manualCrafts = data.getStringList("manual-crafts").toMutableSet()
        if (manualCrafts.add(recipe.id)) {
            data.set("manual-crafts", manualCrafts.toList())
            data.save(file)
        }
    }

    @Throws(IOException::class)
    fun unlock(player: Player, recipeId: String): Boolean {
        val file = playerFile(player.uniqueId)
        if (!file.parentFile.exists() && !file.parentFile.mkdirs()) {
            throw IOException("Could not create player data directory.")
        }
        val unlocked = unlocked(player).toMutableSet()
        if (!unlocked.add(recipeId)) return false
        val data = YamlConfiguration.loadConfiguration(file)
        data.set("blueprints", unlocked.toList())
        data.save(file)
        unlockedByPlayer[player.uniqueId] = unlocked
        return true
    }

    fun unlocked(player: Player): Set<String> = unlockedByPlayer.getOrPut(player.uniqueId) {
        playerData(player.uniqueId).getStringList("blueprints").toMutableSet()
    }.toSet()

    private fun playerData(uuid: UUID): YamlConfiguration = YamlConfiguration.loadConfiguration(playerFile(uuid))

    private fun playerFile(uuid: UUID) = plugin.dataFolder.resolve("players").resolve("$uuid.yml")
}
