package vn.extractionfantasy.synthworks.command

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import vn.extractionfantasy.synthworks.SynthworksPlugin
import vn.extractionfantasy.synthworks.gui.SynthworksGui
import java.util.Locale

class SynthworksCommand(private val plugin: SynthworksPlugin, private val gui: SynthworksGui) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) return showHelp(sender)

        when (args[0].lowercase(Locale.ROOT)) {
            "help" -> return showHelp(sender)
            "reload" -> {
                if (!plugin.hasAdminPermission(sender)) return noPermission(sender)
                plugin.reloadSynthworks()
                sender.sendMessage("§aSynthworks reloaded. Loaded ${plugin.itemManager.itemIds().size} items and ${plugin.recipeManager.all().size} recipes.")
            }
            "editor", "edit" -> {
                if (!plugin.hasAdminPermission(sender)) return noPermission(sender)
                val player = sender as? Player ?: return playerOnly(sender)
                gui.openEditor(player)
            }
            "open", "gui", "player" -> {
                if (!plugin.hasAdminPermission(sender)) return noPermission(sender)
                val target = onlineTarget(sender, args.getOrNull(1)) ?: return true
                gui.openPlayerMenu(target)
                sender.sendMessage("§aOpened the Synthworks menu for ${target.name}.")
            }
            "give" -> return handleGive(sender, args.drop(1))
            "info", "debug" -> {
                if (!plugin.hasAdminPermission(sender)) return noPermission(sender)
                sender.sendMessage("§6Synthworks §7v${plugin.pluginMeta.version}")
                sender.sendMessage("§7Loaded items: §f${plugin.itemManager.itemIds().size}")
                sender.sendMessage("§7Loaded recipes: §f${plugin.recipeManager.all().size}")
                sender.sendMessage("§7Crafter material: §f${plugin.config.getString("settings.crafter.material", "CRAFTER")}")
            }
            else -> {
                if (!plugin.hasAdminPermission(sender)) return noPermission(sender)
                val target = onlineTarget(sender, args[0]) ?: return true
                gui.openPlayerMenu(target)
                sender.sendMessage("§aOpened the Synthworks menu for ${target.name}.")
            }
        }
        return true
    }

    private fun handleGive(sender: CommandSender, args: List<String>): Boolean {
        if (!plugin.hasAdminPermission(sender)) return noPermission(sender)
        return when (args.firstOrNull()?.lowercase(Locale.ROOT)) {
            "item" -> giveItem(sender, args.getOrNull(1), args.getOrNull(2), args.getOrNull(3))
            "crafter" -> giveStation(sender, args.getOrNull(1))
            "blueprint" -> giveBlueprint(sender, args.getOrNull(1), args.getOrNull(2))
            else -> {
                sender.sendMessage("§eUsage: /synthworks give <item|crafter|blueprint> ...")
                true
            }
        }
    }

    private fun giveItem(sender: CommandSender, playerName: String?, id: String?, amountText: String?): Boolean {
        if (!plugin.hasAdminPermission(sender)) return noPermission(sender)
        if (playerName == null || id == null) {
            sender.sendMessage("§eUsage: /synthworks give item <player> <id> [amount]")
            return true
        }
        val target = onlineTarget(sender, playerName) ?: return true
        val definition = plugin.itemManager.definition(id)
        if (definition == null) {
            sender.sendMessage("§cUnknown item ID: $id")
            return true
        }
        val amount = if (amountText == null) definition.amount else amountText.toIntOrNull()
        if (amount == null || amount < 1) {
            sender.sendMessage("§cAmount must be a positive whole number.")
            return true
        }
        val stack = plugin.itemManager.create(id, amount)
        if (stack == null) {
            sender.sendMessage("§cCould not create item '$id'. Check the server log.")
            return true
        }
        giveTo(target, stack)
        sender.sendMessage("§aGave $amount x $id to ${target.name}.")
        target.sendMessage("§aYou received $amount x ${definition.name.replace('&', '§')}.")
        return true
    }

    private fun giveStation(sender: CommandSender, playerName: String?): Boolean {
        if (!plugin.hasAdminPermission(sender)) return noPermission(sender)
        if (playerName == null) {
            sender.sendMessage("§eUsage: /synthworks give crafter <player>")
            return true
        }
        val target = onlineTarget(sender, playerName) ?: return true
        giveTo(target, plugin.crafterManager.createItem())
        sender.sendMessage("§aGave a Synthworks Crafter to ${target.name}.")
        target.sendMessage("§dYou received a Synthworks Crafter.")
        return true
    }

    private fun giveBlueprint(sender: CommandSender, playerName: String?, recipeId: String?): Boolean {
        if (!plugin.hasAdminPermission(sender)) return noPermission(sender)
        if (playerName == null || recipeId == null) {
            sender.sendMessage("§eUsage: /synthworks give blueprint <player> <recipe_id>")
            return true
        }
        val target = onlineTarget(sender, playerName) ?: return true
        val recipe = plugin.recipeManager.recipe(recipeId)
        if (recipe == null) {
            sender.sendMessage("§cUnknown recipe ID: $recipeId")
            return true
        }
        giveTo(target, plugin.blueprintManager.create(recipe))
        sender.sendMessage("§aGave blueprint '$recipeId' to ${target.name}.")
        target.sendMessage("§bYou received the blueprint for '$recipeId'.")
        return true
    }

    private fun onlineTarget(sender: CommandSender, name: String?): Player? {
        if (name == null) {
            sender.sendMessage("§eUsage: /synthworks open <player>")
            return null
        }
        return Bukkit.getPlayerExact(name) ?: run {
            sender.sendMessage("§cPlayer is not online: $name")
            null
        }
    }

    private fun giveTo(player: Player, stack: org.bukkit.inventory.ItemStack) {
        player.inventory.addItem(stack).values.forEach { player.world.dropItemNaturally(player.location, it) }
    }

    private fun showHelp(sender: CommandSender): Boolean {
        sender.sendMessage("§6Synthworks §7v${plugin.pluginMeta.version}")
        sender.sendMessage("§e/synthworks open <player> §7- Open a player's menu")
        sender.sendMessage("§e/synthworks editor §7- Open the editor")
        sender.sendMessage("§e/synthworks give item <player> <id> [amount]")
        sender.sendMessage("§e/synthworks give crafter <player>")
        sender.sendMessage("§e/synthworks give blueprint <player> <recipe_id>")
        sender.sendMessage("§e/synthworks reload §7| §e/synthworks info")
        sender.sendMessage("§7Alias: /sw")
        return true
    }

    private fun playerOnly(sender: CommandSender): Boolean {
        sender.sendMessage("§cThis menu can only be opened in-game.")
        return true
    }

    private fun noPermission(sender: CommandSender): Boolean {
        sender.sendMessage("§cYou don't have permission.")
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): MutableList<String> {
        val onlinePlayers = Bukkit.getOnlinePlayers().map { it.name }
        return when (args.size) {
            1 -> complete(
                listOf("help", "open", "editor", "give", "reload", "info", "debug", "edit", "gui", "player") +
                    onlinePlayers,
                args[0]
            )
            2 -> when (args[0].lowercase(Locale.ROOT)) {
                "open", "gui", "player" -> complete(onlinePlayers, args[1])
                "give" -> complete(listOf("item", "crafter", "blueprint"), args[1])
                else -> mutableListOf()
            }
            3 -> when (args[0].lowercase(Locale.ROOT)) {
                "give" -> when (args[1].lowercase(Locale.ROOT)) {
                    "item", "blueprint", "crafter" -> complete(onlinePlayers, args[2])
                    else -> mutableListOf()
                }
                else -> mutableListOf()
            }
            4 -> when {
                args[0].equals("give", true) && args[1].equals("item", true) ->
                    complete(plugin.itemManager.itemIds(), args[3])
                args[0].equals("give", true) && args[1].equals("blueprint", true) ->
                    complete(plugin.recipeManager.all().map { it.id }, args[3])
                else -> mutableListOf()
            }
            5 -> if (args[0].equals("give", true) && args[1].equals("item", true)) {
                complete(listOf("1", "16", "32", "64"), args[4])
            } else mutableListOf()
            else -> mutableListOf()
        }
    }

    private fun complete(candidates: Iterable<String>, prefix: String): MutableList<String> =
        candidates.filter { it.startsWith(prefix, ignoreCase = true) }.sorted().toMutableList()
}
