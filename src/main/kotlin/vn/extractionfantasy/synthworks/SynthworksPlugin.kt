package vn.extractionfantasy.synthworks

import org.bukkit.NamespacedKey
import org.bukkit.plugin.java.JavaPlugin
import vn.extractionfantasy.synthworks.block.CrafterManager
import vn.extractionfantasy.synthworks.blueprint.BlueprintManager
import vn.extractionfantasy.synthworks.command.SynthworksCommand
import vn.extractionfantasy.synthworks.gui.SynthworksGui
import vn.extractionfantasy.synthworks.item.ItemManager
import vn.extractionfantasy.synthworks.listener.CustomItemListener
import vn.extractionfantasy.synthworks.recipe.RecipeManager
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption

class SynthworksPlugin : JavaPlugin() {
    lateinit var itemIdKey: NamespacedKey
        private set
    lateinit var legacyItemIdKey: NamespacedKey
        private set
    lateinit var markerKey: NamespacedKey
        private set
    lateinit var legacyMarkerKey: NamespacedKey
        private set
    lateinit var blueprintKey: NamespacedKey
        private set
    lateinit var legacyBlueprintKey: NamespacedKey
        private set
    lateinit var crafterItemKey: NamespacedKey
        private set
    lateinit var legacyCrafterItemKey: NamespacedKey
        private set

    lateinit var itemManager: ItemManager
        private set
    lateinit var recipeManager: RecipeManager
        private set
    lateinit var blueprintManager: BlueprintManager
        private set
    lateinit var crafterManager: CrafterManager
        private set

    override fun onEnable() {
        try {
            migrateLegacyDataDirectory()
        } catch (exception: IOException) {
            logger.severe("Could not migrate data from the old Synthesis folder: ${exception.message}")
            server.pluginManager.disablePlugin(this)
            return
        }
        saveDefaultConfig()
        migrateLegacyConfig()
        if (!dataFolder.resolve("custom_items.yml").exists() && !dataFolder.resolve("custom_items").exists()) {
            saveResourceIfMissing("custom_items/refined/refined_steel.yml")
            saveResourceIfMissing("custom_items/ctier/ctier_tactical_vest.yml")
        }

        itemIdKey = NamespacedKey(this, "item_id")
        legacyItemIdKey = NamespacedKey("synthesis", "item_id")
        markerKey = NamespacedKey(this, "synthworks_item")
        legacyMarkerKey = NamespacedKey("synthesis", "synthesis_item")
        blueprintKey = NamespacedKey(this, "blueprint_recipe")
        legacyBlueprintKey = NamespacedKey("synthesis", "blueprint_recipe")
        crafterItemKey = NamespacedKey(this, "crafter_item")
        legacyCrafterItemKey = NamespacedKey("synthesis", "synthesizer_item")

        itemManager = ItemManager(this)
        itemManager.reload()
        recipeManager = RecipeManager(this)
        recipeManager.reload()
        blueprintManager = BlueprintManager(this)
        crafterManager = CrafterManager(this)
        crafterManager.load()
        crafterManager.startParticles()

        val gui = SynthworksGui(this)
        server.pluginManager.registerEvents(gui, this)
        server.pluginManager.registerEvents(CustomItemListener(this, gui), this)

        val command = getCommand("synthworks")
        if (command == null) {
            logger.severe("Command 'synthworks' is missing from plugin.yml")
            server.pluginManager.disablePlugin(this)
            return
        }

        val handler = SynthworksCommand(this, gui)
        command.setExecutor(handler)
        command.tabCompleter = handler

        logger.info("Synthworks v${pluginMeta.version} enabled. Loaded ${itemManager.itemIds().size} custom items.")
    }

    fun hasAdminPermission(sender: org.bukkit.command.CommandSender): Boolean =
        sender.hasPermission("synthworks.admin") || sender.hasPermission("synthesis.admin")

    fun hasUsePermission(player: org.bukkit.entity.Player): Boolean =
        player.hasPermission("synthworks.use") || player.hasPermission("synthesis.use")

    private fun migrateLegacyDataDirectory() {
        val oldDirectory = dataFolder.parentFile.resolve("Synthesis")
        if (!oldDirectory.isDirectory || oldDirectory.absoluteFile == dataFolder.absoluteFile) return

        val oldRoot = oldDirectory.toPath()
        val newRoot = dataFolder.toPath()
        Files.walk(oldRoot).use { paths ->
            paths.sorted().forEach { source ->
                val destination = newRoot.resolve(oldRoot.relativize(source))
                when {
                    Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS) -> Files.createDirectories(destination)
                    Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) -> {
                        Files.createDirectories(destination.parent)
                        if (Files.notExists(destination)) {
                            Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES)
                        } else {
                            logger.warning("Keeping existing Synthworks data at '${destination.fileName}'; old Synthesis data was not overwritten.")
                        }
                    }
                    else -> logger.warning("Skipping unsupported file in old Synthesis data folder: $source")
                }
            }
        }
        logger.info("Migrated available data from the old Synthesis folder. The original folder was left untouched.")
    }

    private fun migrateLegacyConfig() {
        val pluginConfig = config
        var changed = false
        val legacyTableMaterial = "settings.table.material"
        val newTableMaterial = "settings.crafter.material"
        if (pluginConfig.contains(legacyTableMaterial, true) && !pluginConfig.contains(newTableMaterial, true)) {
            val oldMaterial = pluginConfig.getString(legacyTableMaterial)
            pluginConfig.set(newTableMaterial, if (oldMaterial.equals("SMITHING_TABLE", true)) "CRAFTER" else oldMaterial)
            pluginConfig.set(legacyTableMaterial, null)
            changed = true
        }

        for (setting in listOf(
            "custom-model-data",
            "particle-interval-ticks",
            "particle-count",
            "particle-player-range"
        )) {
            val oldPath = "settings.synthesizer.$setting"
            val newPath = "settings.crafter.$setting"
            if (pluginConfig.contains(oldPath, true) && !pluginConfig.contains(newPath, true)) {
                pluginConfig.set(newPath, pluginConfig.get(oldPath))
                changed = true
            }
            if (pluginConfig.contains(oldPath, true)) {
                pluginConfig.set(oldPath, null)
                changed = true
            }
        }
        if (changed) pluginConfig.save(dataFolder.resolve("config.yml"))
    }

    private fun saveResourceIfMissing(path: String) {
        val file = dataFolder.resolve(path)
        if (!file.exists()) saveResource(path, false)
    }

    fun reloadSynthworks() {
        reloadConfig()
        itemManager.reload()
        recipeManager.reload()
    }
}
