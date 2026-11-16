package vn.extractionfantasy.synthworks.listener

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.inventory.CraftItemEvent
import org.bukkit.event.inventory.PrepareItemCraftEvent
import org.bukkit.Material
import vn.extractionfantasy.synthworks.SynthworksPlugin
import vn.extractionfantasy.synthworks.gui.SynthworksGui

class CustomItemListener(private val plugin: SynthworksPlugin, private val gui: SynthworksGui) : Listener {
    @EventHandler(ignoreCancelled = true)
    fun onPlayerInteract(event: PlayerInteractEvent) {
        val player = event.player
        if (event.action != org.bukkit.event.block.Action.RIGHT_CLICK_AIR &&
            event.action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
        ) return

        val recipeId = plugin.blueprintManager.recipeId(event.item)
        if (recipeId != null) {
            event.isCancelled = true
            val recipe = plugin.recipeManager.recipe(recipeId)
            if (recipe == null) {
                player.sendMessage("§cThis blueprint refers to an unknown recipe.")
            } else {
                gui.openBlueprint(player, recipe, event.hand)
            }
            return
        }

        val clicked = event.clickedBlock ?: return
        if (event.action == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK &&
            plugin.crafterManager.isCrafter(clicked.location)
        ) {
            event.isCancelled = true
            if (plugin.hasUsePermission(player)) gui.openPlayerMenu(player)
            else player.sendMessage("§cYou don't have permission to use Synthworks.")
            return
        }

        val materialName = plugin.config.getString("settings.crafter.material", "CRAFTER")!!
        val tableMaterial = Material.matchMaterial(materialName)
        if (tableMaterial == null) {
            plugin.logger.warning("Invalid Synthworks Crafter material '$materialName'.")
            return
        }
        if (event.action == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK && clicked.type == tableMaterial) {
            event.isCancelled = true
            if (plugin.hasUsePermission(player)) gui.openPlayerMenu(player)
            else player.sendMessage("§cYou don't have permission to use Synthworks.")
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val item = event.itemInHand
        if (plugin.crafterManager.isItem(item)) {
            try {
                plugin.crafterManager.register(event.blockPlaced.location)
                event.player.sendMessage("§dSynthworks Crafter placed.")
            } catch (exception: java.io.IOException) {
                event.isCancelled = true
                plugin.logger.severe("Could not register Synthworks Crafter: ${exception.message}")
                event.player.sendMessage("§cCould not place Synthworks Crafter: ${exception.message}")
            }
            return
        }
        if (plugin.itemManager.isSynthworksItem(item) && !plugin.itemManager.canPlace(item)) {
            event.isCancelled = true
            event.player.sendMessage("§cYou cannot place this Synthworks item.")
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockBreak(event: org.bukkit.event.block.BlockBreakEvent) {
        if (!plugin.crafterManager.isCrafter(event.block.location)) return
        try {
            plugin.crafterManager.unregister(event.block.location)
            event.isDropItems = false
            event.block.world.dropItemNaturally(event.block.location, plugin.crafterManager.createItem())
        } catch (exception: java.io.IOException) {
            event.isCancelled = true
            plugin.logger.severe("Could not unregister Synthworks Crafter: ${exception.message}")
            event.player.sendMessage("§cCould not break Synthworks Crafter: ${exception.message}")
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onBlockExplode(event: org.bukkit.event.block.BlockExplodeEvent) {
        unregisterExplodedCrafters(event.blockList().map { it.location })
    }

    @EventHandler(ignoreCancelled = true)
    fun onEntityExplode(event: org.bukkit.event.entity.EntityExplodeEvent) {
        unregisterExplodedCrafters(event.blockList().map { it.location })
    }

    private fun unregisterExplodedCrafters(locations: List<org.bukkit.Location>) {
        try {
            plugin.crafterManager.unregisterAll(locations)
        } catch (exception: java.io.IOException) {
            plugin.logger.severe("Could not unregister exploded Synthworks Crafters: ${exception.message}")
        }
    }

    @EventHandler
    fun onPrepareCraft(event: PrepareItemCraftEvent) {
        val hasBlockedItem = event.inventory.matrix.any {
            plugin.itemManager.isSynthworksItem(it) && !plugin.itemManager.canUseInVanillaCrafting(it)
        }
        if (hasBlockedItem) event.inventory.result = null
    }

    @EventHandler(ignoreCancelled = true)
    fun onCraft(event: CraftItemEvent) {
        val hasBlockedItem = event.inventory.matrix.any {
            plugin.itemManager.isSynthworksItem(it) && !plugin.itemManager.canUseInVanillaCrafting(it)
        }
        if (hasBlockedItem) event.isCancelled = true
    }
}
