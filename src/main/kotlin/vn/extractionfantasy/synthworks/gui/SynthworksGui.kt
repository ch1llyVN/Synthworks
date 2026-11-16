package vn.extractionfantasy.synthworks.gui

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import vn.extractionfantasy.synthworks.SynthworksPlugin
import vn.extractionfantasy.synthworks.item.AttributeDefinition
import vn.extractionfantasy.synthworks.recipe.SynthworksRecipe
import java.io.IOException
import java.util.UUID

class SynthworksGui(private val plugin: SynthworksPlugin) : Listener {
    private val inputSlots = (18..21).toList() + (23..26).toList()
    private val requirementSlots = (0..3).toList() + (5..8).toList()
    private val recipeEditorSlots = inputSlots
    private val craftBackSlot = 27
    private val craftQuickSlot = 31
    private val craftConfirmSlot = 35
    private val prompts = java.util.concurrent.ConcurrentHashMap<UUID, Prompt>()
    private val itemDrafts = mutableMapOf<UUID, ItemDraft>()

    fun openPlayerMenu(player: Player) {
        val holder = menu(SynthworksMenuHolder.Type.MAIN, 27, "&8Synthworks")
        holder.menu.setItem(11, button(Material.BOOK, "&eRecipe Book", "&7Browse available recipes"))
        holder.menu.setItem(15, button(Material.ENDER_CHEST, "&bBlueprint Storage", "&7View unlocked blueprints"))
        holder.menu.setItem(22, button(Material.RED_STAINED_GLASS_PANE, "&cClose"))
        player.openInventory(holder.menu)
    }

    fun openEditor(player: Player) {
        if (!plugin.hasAdminPermission(player)) {
            player.sendMessage("§cYou don't have permission.")
            return
        }
        val holder = menu(SynthworksMenuHolder.Type.EDITOR_HOME, 27, "&8Synthworks Editor")
        holder.menu.setItem(10, button(Material.ANVIL, "&aCreate custom item"))
        holder.menu.setItem(12, button(Material.CRAFTING_TABLE, "&aCreate recipe"))
        holder.menu.setItem(14, button(Material.BOOK, "&eManage recipes"))
        holder.menu.setItem(16, button(Material.CHEST, "&eManage items"))
        player.openInventory(holder.menu)
    }

    fun openBlueprint(player: Player, recipe: SynthworksRecipe, hand: org.bukkit.inventory.EquipmentSlot?) {
        if (plugin.blueprintManager.isUnlocked(player, recipe.id)) {
            player.sendMessage("§eYou have already unlocked this recipe.")
            return
        }
        try {
            if (plugin.blueprintManager.unlock(player, recipe.id)) {
                val held = if (hand == org.bukkit.inventory.EquipmentSlot.OFF_HAND) player.inventory.itemInOffHand else player.inventory.itemInMainHand
                if (held.amount <= 1) {
                    if (hand == org.bukkit.inventory.EquipmentSlot.OFF_HAND) player.inventory.setItemInOffHand(null)
                    else player.inventory.setItemInMainHand(null)
                }
                else held.amount -= 1
                player.sendMessage("§aRecipe unlocked: §f${recipe.id}")
            }
        } catch (exception: IOException) {
            plugin.logger.severe("Could not save blueprint unlock for ${player.uniqueId}: ${exception.message}")
            player.sendMessage("§cCould not save your blueprint unlock. Please contact an administrator.")
        }
    }

    private fun openCategories(player: Player) {
        val holder = menu(SynthworksMenuHolder.Type.CATEGORIES, 54, "&8Recipe Book")
        plugin.recipeManager.categories().forEachIndexed { index, category ->
            if (index >= 45) return@forEachIndexed
            holder.menu.setItem(index, button(Material.CHEST, "&e${category.displayName()}", "&7${plugin.recipeManager.inCategory(category).size} recipes"))
        }
        holder.menu.setItem(49, button(Material.RED_STAINED_GLASS_PANE, "&cBack"))
        player.openInventory(holder.menu)
    }

    private fun openRecipes(player: Player, category: String) {
        val holder = menu(SynthworksMenuHolder.Type.RECIPE_LIST, 54, "&8${category.displayName()}")
        holder.context = category
        plugin.recipeManager.inCategory(category).take(45).forEachIndexed { index, recipe ->
            val output = plugin.itemManager.create(recipe.resultItemId) ?: button(Material.BARRIER, "&cMissing item: ${recipe.resultItemId}")
            val unlocked = !recipe.requiresBlueprint || plugin.blueprintManager.isUnlocked(player, recipe.id)
            output.itemMeta = output.itemMeta.apply {
                displayName(plugin.itemManager.legacy.deserialize("${if (unlocked) "&a" else "&c"}${recipeDisplayName(recipe)}"))
                lore(listOf(plugin.itemManager.legacy.deserialize(if (unlocked) "&7Click to synthesize" else "&cRequires a blueprint")))
            }
            holder.menu.setItem(index, output)
        }
        holder.menu.setItem(49, button(Material.RED_STAINED_GLASS_PANE, "&cBack"))
        player.openInventory(holder.menu)
    }

    private fun openBlueprints(player: Player) {
        val holder = menu(SynthworksMenuHolder.Type.BLUEPRINTS, 54, "&8Blueprint Storage")
        plugin.blueprintManager.unlocked(player).mapNotNull(plugin.recipeManager::recipe).take(45).forEachIndexed { index, recipe ->
            val icon = plugin.itemManager.create(recipe.resultItemId) ?: ItemStack(Material.PAPER)
            icon.itemMeta = icon.itemMeta.apply {
                displayName(plugin.itemManager.legacy.deserialize("&b${recipeDisplayName(recipe)}"))
                lore(listOf(plugin.itemManager.legacy.deserialize("&7Click to synthesize")))
            }
            holder.menu.setItem(index, icon)
        }
        holder.menu.setItem(49, button(Material.RED_STAINED_GLASS_PANE, "&cBack"))
        player.openInventory(holder.menu)
    }

    private fun openCraft(player: Player, recipe: SynthworksRecipe, fromBlueprintStorage: Boolean = false) {
        val holder = menu(SynthworksMenuHolder.Type.CRAFT, 36, "&8Craft: ${recipeDisplayName(recipe)}")
        holder.context = recipe.id
        holder.data["fromBlueprintStorage"] = fromBlueprintStorage
        holder.data["category"] = recipe.category
        recipe.ingredients.forEachIndexed { index, ingredient ->
            if (index < requirementSlots.size) {
                val display = ingredient.clone()
                display.itemMeta = display.itemMeta.apply {
                    lore((display.itemMeta.lore() ?: emptyList()) + listOf(plugin.itemManager.legacy.deserialize("&eRequired: ${ingredient.amount}")))
                }
                holder.menu.setItem(requirementSlots[index], display)
            }
        }
        plugin.itemManager.create(recipe.resultItemId)?.let { holder.menu.setItem(13, it) }
        inputSlots.forEach { holder.menu.setItem(it, null) }
        holder.menu.setItem(craftBackSlot, button(Material.RED_STAINED_GLASS_PANE, "&cBack"))
        holder.menu.setItem(craftQuickSlot, button(Material.CYAN_STAINED_GLASS_PANE, "&bQuick craft", "&7Use ingredients from your inventory"))
        holder.menu.setItem(craftConfirmSlot, button(Material.LIME_STAINED_GLASS_PANE, "&aManual craft", "&7Craft with submitted ingredients"))
        player.openInventory(holder.menu)
    }

    private fun openItemEditor(player: Player, id: String? = null) {
        val holder = menu(SynthworksMenuHolder.Type.ITEM_EDITOR, 27, if (id == null) "&8Create Custom Item" else "&8Edit: $id")
        val existing = id?.let(plugin.itemManager::definition)
        val draft = ItemDraft(
            id = id,
            blockPlacement = existing?.blockPlacement ?: false,
            vanillaCrafting = existing?.vanillaCrafting ?: false,
            glint = existing?.glint ?: false,
            unbreakable = existing?.unbreakable ?: false,
            durability = existing?.durability ?: 0,
            maxStackSize = existing?.maxStackSize,
            attributes = existing?.attributes?.toMutableMap() ?: linkedMapOf()
        )
        itemDrafts[player.uniqueId] = draft
        if (id != null) {
            plugin.itemManager.create(id)?.let { holder.menu.setItem(13, it) }
        } else {
            holder.menu.setItem(13, button(Material.YELLOW_STAINED_GLASS_PANE, "&ePlace an item template here"))
        }
        holder.menu.setItem(10, toggle(Material.OBSIDIAN, "Block placement", draft.blockPlacement))
        holder.menu.setItem(11, toggle(Material.CRAFTING_TABLE, "Vanilla crafting", draft.vanillaCrafting))
        holder.menu.setItem(12, toggle(Material.GLOWSTONE_DUST, "Glint", draft.glint))
        holder.menu.setItem(14, toggle(Material.IRON_CHESTPLATE, "Unbreakable", draft.unbreakable))
        holder.menu.setItem(15, button(Material.ANVIL, "&eSet durability", "&7Current damage: ${draft.durability}"))
        holder.menu.setItem(16, button(Material.IRON_INGOT, "&eEdit attributes", "&7ATTRIBUTE:amount:operation,...", "&7Use - to clear"))
        holder.menu.setItem(20, button(Material.NAME_TAG, "&eEdit name", "&7Current name is taken from the template"))
        holder.menu.setItem(22, button(Material.WRITABLE_BOOK, "&eEdit lore", "&7Enter lines separated by |"))
        holder.menu.setItem(24, button(Material.HOPPER, "&eSet max stack size", "&7Current: ${draft.maxStackSize ?: "material default"}"))
        holder.menu.setItem(18, button(Material.RED_STAINED_GLASS_PANE, "&cBack"))
        holder.menu.setItem(26, button(Material.LIME_STAINED_GLASS_PANE, "&aSave item"))
        holder.context = id
        player.openInventory(holder.menu)
    }

    private fun refreshItemEditor(player: Player, holder: SynthworksMenuHolder) {
        val draft = itemDrafts[player.uniqueId] ?: return
        holder.menu.setItem(10, toggle(Material.OBSIDIAN, "Block placement", draft.blockPlacement))
        holder.menu.setItem(11, toggle(Material.CRAFTING_TABLE, "Vanilla crafting", draft.vanillaCrafting))
        holder.menu.setItem(12, toggle(Material.GLOWSTONE_DUST, "Glint", draft.glint))
        holder.menu.setItem(14, toggle(Material.IRON_CHESTPLATE, "Unbreakable", draft.unbreakable))
        holder.menu.setItem(15, button(Material.ANVIL, "&eSet durability", "&7Current damage: ${draft.durability}"))
        holder.menu.setItem(24, button(Material.HOPPER, "&eSet max stack size", "&7Current: ${draft.maxStackSize ?: "material default"}"))
    }

    private fun openItemList(player: Player) {
        val holder = menu(SynthworksMenuHolder.Type.ITEM_CATEGORIES, 54, "&8Custom Item Categories")
        val ids = plugin.itemManager.itemIds()
        val categories = ids.map(::itemCategory).distinct().sorted()
        holder.data["categories"] = categories
        categories.take(45).forEachIndexed { index, category ->
            val count = ids.count { itemCategory(it) == category }
            holder.menu.setItem(index, button(Material.CHEST, "&e${category.displayName()}", "&7$count items"))
        }
        holder.menu.setItem(49, button(Material.RED_STAINED_GLASS_PANE, "&cBack"))
        player.openInventory(holder.menu)
    }

    private fun openItemList(player: Player, category: String) {
        val holder = menu(SynthworksMenuHolder.Type.ITEM_LIST, 54, "&8Custom Items")
        holder.context = category
        val ids = plugin.itemManager.itemIds().filter { itemCategory(it) == category }.sorted().take(45)
        holder.data["ids"] = ids
        (holder.data["ids"] as List<String>).forEachIndexed { index, id ->
            plugin.itemManager.create(id)?.let { holder.menu.setItem(index, it) }
        }
        holder.menu.setItem(49, button(Material.RED_STAINED_GLASS_PANE, "&cBack"))
        player.openInventory(holder.menu)
    }

    private fun openItemDetail(player: Player, id: String) {
        val holder = menu(SynthworksMenuHolder.Type.ITEM_DETAIL, 27, "&8Item: $id")
        holder.context = id
        holder.data["category"] = itemCategory(id)
        plugin.itemManager.create(id)?.let { holder.menu.setItem(4, it) }
        holder.menu.setItem(11, button(Material.WRITABLE_BOOK, "&eEdit"))
        holder.menu.setItem(13, button(Material.LIME_STAINED_GLASS_PANE, "&aGive item"))
        holder.menu.setItem(15, button(Material.TNT, "&cDelete"))
        holder.menu.setItem(22, button(Material.RED_STAINED_GLASS_PANE, "&7Back"))
        player.openInventory(holder.menu)
    }

    private fun openRecipeCategories(player: Player) {
        val holder = menu(SynthworksMenuHolder.Type.RECIPE_CATEGORIES, 54, "&8Recipe Categories")
        val categories = plugin.recipeManager.categories()
        holder.data["categories"] = categories
        categories.take(45).forEachIndexed { index, category ->
            val count = plugin.recipeManager.inCategory(category).size
            holder.menu.setItem(index, button(Material.CHEST, "&e${category.displayName()}", "&7$count recipes"))
        }
        holder.menu.setItem(49, button(Material.RED_STAINED_GLASS_PANE, "&cBack"))
        player.openInventory(holder.menu)
    }

    private fun openRecipeListAdmin(player: Player, category: String) {
        val holder = menu(SynthworksMenuHolder.Type.RECIPE_LIST_ADMIN, 54, "&8${category.displayName()} Recipes")
        holder.context = category
        val recipes = plugin.recipeManager.inCategory(category).take(45)
        holder.data["ids"] = recipes.map { it.id }
        recipes.forEachIndexed { index, recipe ->
            val item = plugin.itemManager.create(recipe.resultItemId) ?: ItemStack(Material.BARRIER)
            item.itemMeta = item.itemMeta.apply {
                displayName(plugin.itemManager.legacy.deserialize("&e${recipe.id}"))
                lore(listOf(plugin.itemManager.legacy.deserialize("&7Category: ${recipe.category}")))
            }
            holder.menu.setItem(index, item)
        }
        holder.menu.setItem(49, button(Material.RED_STAINED_GLASS_PANE, "&cBack to categories"))
        player.openInventory(holder.menu)
    }

    private fun openRecipeDetail(player: Player, id: String) {
        val recipe = plugin.recipeManager.recipe(id) ?: return
        val holder = menu(SynthworksMenuHolder.Type.RECIPE_DETAIL, 27, "&8Recipe: $id")
        holder.context = id
        holder.data["category"] = recipe.category
        holder.menu.setItem(11, button(Material.WRITABLE_BOOK, "&eEdit recipe"))
        val result = plugin.itemManager.create(recipe.resultItemId) ?: ItemStack(Material.BARRIER)
        result.itemMeta = result.itemMeta.apply {
            lore(listOf(plugin.itemManager.legacy.deserialize("&7Click to give this item")))
        }
        holder.menu.setItem(13, result)
        holder.menu.setItem(15, button(Material.PAPER, "&bGive blueprint"))
        holder.menu.setItem(22, button(Material.TNT, "&cDelete recipe"))
        holder.menu.setItem(26, button(Material.RED_STAINED_GLASS_PANE, "&7Back"))
        player.openInventory(holder.menu)
    }

    private fun openRecipeEditor(player: Player, id: String? = null) {
        val existing = id?.let(plugin.recipeManager::recipe)
        val holder = menu(SynthworksMenuHolder.Type.RECIPE_EDITOR, 54, if (id == null) "&8Create Recipe" else "&8Edit Recipe: $id")
        holder.context = id
        holder.data["category"] = existing?.category ?: "misc"
        holder.data["requiresBlueprint"] = existing?.requiresBlueprint ?: false
        holder.data["baselineIngredients"] = mutableMapOf<Int, ItemStack>()
        holder.data["borrowedIngredients"] = mutableSetOf<Int>()
        (recipeEditorSlots + 13).forEach { holder.menu.setItem(it, null) }
        existing?.ingredients?.forEachIndexed { index, item ->
            if (index < recipeEditorSlots.size) {
                val slot = recipeEditorSlots[index]
                holder.menu.setItem(slot, item.clone())
                setRecipeRequirementPreview(holder, index, item)
                @Suppress("UNCHECKED_CAST")
                (holder.data["baselineIngredients"] as MutableMap<Int, ItemStack>)[slot] = item.clone()
            }
        }
        existing?.let { recipe -> plugin.itemManager.create(recipe.resultItemId)?.let { holder.menu.setItem(13, it) } }
        holder.menu.setItem(47, toggle(Material.PAPER, "Requires blueprint", holder.data["requiresBlueprint"] as Boolean))
        holder.menu.setItem(51, button(Material.CHEST, "&eCategory: ${holder.data["category"]}", "&7Click to change"))
        holder.menu.setItem(22, button(Material.COMPASS, "&eChoose output item"))
        holder.menu.setItem(craftBackSlot, button(Material.RED_STAINED_GLASS_PANE, "&cBack"))
        holder.menu.setItem(craftConfirmSlot, button(Material.LIME_STAINED_GLASS_PANE, "&aSave recipe"))
        player.openInventory(holder.menu)
    }

    private fun setRecipeRequirementPreview(holder: SynthworksMenuHolder, index: Int, ingredient: ItemStack) {
        val slot = requirementSlots.getOrNull(index) ?: return
        val preview = ingredient.clone()
        preview.itemMeta = preview.itemMeta.apply {
            lore((preview.itemMeta.lore() ?: emptyList()) +
                listOf(plugin.itemManager.legacy.deserialize("&eRequired: ${ingredient.amount}")))
        }
        holder.menu.setItem(slot, preview)
    }

    private fun refreshRecipeRequirement(holder: SynthworksMenuHolder, inputSlot: Int) {
        val index = recipeEditorSlots.indexOf(inputSlot)
        if (index < 0) return
        val requirementSlot = requirementSlots[index]
        val ingredient = holder.menu.getItem(inputSlot)?.takeUnless { it.type.isAir }
        if (ingredient == null) {
            holder.menu.setItem(requirementSlot, button(Material.GRAY_STAINED_GLASS_PANE, " "))
        } else {
            setRecipeRequirementPreview(holder, index, ingredient)
        }
    }

    private fun menu(type: SynthworksMenuHolder.Type, size: Int, title: String): SynthworksMenuHolder {
        val holder = SynthworksMenuHolder(type)
        holder.menu = Bukkit.createInventory(holder, size, plugin.itemManager.legacy.deserialize(title))
        val filler = button(Material.GRAY_STAINED_GLASS_PANE, " ")
        for (slot in 0 until size) holder.menu.setItem(slot, filler.clone())
        return holder
    }

    private fun button(material: Material, name: String, vararg lore: String): ItemStack {
        val item = ItemStack(material)
        item.itemMeta = item.itemMeta.apply {
            displayName(plugin.itemManager.legacy.deserialize(name))
            if (lore.isNotEmpty()) lore(lore.map(plugin.itemManager.legacy::deserialize))
        }
        return item
    }

    private fun toggle(material: Material, name: String, enabled: Boolean): ItemStack =
        button(material, "&e$name", if (enabled) "&aEnabled" else "&cDisabled")

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        val holder = event.view.topInventory.holder as? SynthworksMenuHolder ?: return
        val player = event.whoClicked as? Player ?: return
        val slot = event.rawSlot
        if (slot < 0) {
            event.isCancelled = true
            return
        }
        if (slot >= event.view.topInventory.size) {
            if (event.isShiftClick || event.click == org.bukkit.event.inventory.ClickType.DOUBLE_CLICK) {
                event.isCancelled = true
            }
            return
        }

        if (holder.type == SynthworksMenuHolder.Type.CRAFT && slot in inputSlots) {
            handleIngredientClick(event, holder.menu, slot)
            return
        }
        if (holder.type == SynthworksMenuHolder.Type.ITEM_EDITOR && slot == 13) {
            handleItemTemplateClick(event, holder)
            return
        }
        if (holder.type == SynthworksMenuHolder.Type.RECIPE_EDITOR && slot in recipeEditorSlots) {
            handleRecipeEditorIngredientClick(event, holder, slot)
            refreshRecipeRequirement(holder, slot)
            return
        }
        event.isCancelled = true

        when (holder.type) {
            SynthworksMenuHolder.Type.MAIN -> when (slot) {
                11 -> openCategories(player)
                15 -> openBlueprints(player)
                22 -> player.closeInventory()
            }
            SynthworksMenuHolder.Type.CATEGORIES -> if (slot == 49) openPlayerMenu(player) else {
                val category = if (slot in 0..44) plugin.recipeManager.categories().getOrNull(slot) else null
                if (category != null) openRecipes(player, category)
            }
            SynthworksMenuHolder.Type.RECIPE_LIST -> if (slot == 49) openCategories(player) else {
                val recipe = if (slot in 0..44) holder.context?.let(plugin.recipeManager::inCategory)?.getOrNull(slot) else null
                if (recipe != null) selectRecipe(player, recipe)
            }
            SynthworksMenuHolder.Type.BLUEPRINTS -> if (slot == 49) openPlayerMenu(player) else {
                val recipe = if (slot in 0..44) plugin.blueprintManager.unlocked(player).mapNotNull(plugin.recipeManager::recipe).getOrNull(slot) else null
                if (recipe != null) openCraft(player, recipe, fromBlueprintStorage = true)
            }
            SynthworksMenuHolder.Type.CRAFT -> when (slot) {
                craftConfirmSlot -> confirmCraft(player, holder)
                craftQuickSlot -> quickCraft(player, holder)
                craftBackSlot -> {
                    if (holder.data["fromBlueprintStorage"] == true) openBlueprints(player)
                    else openRecipes(player, holder.data["category"] as? String ?: "misc")
                }
            }
            SynthworksMenuHolder.Type.EDITOR_HOME -> when (slot) {
                10 -> openItemEditor(player)
                12 -> openRecipeEditor(player)
                14 -> openRecipeCategories(player)
                16 -> openItemList(player)
            }
            SynthworksMenuHolder.Type.ITEM_CATEGORIES -> if (slot == 49) openEditor(player) else {
                val category = if (slot in 0..44) (holder.data["categories"] as? List<String>)?.getOrNull(slot) else null
                if (category != null) openItemList(player, category)
            }
            SynthworksMenuHolder.Type.ITEM_EDITOR -> onItemEditorClick(player, holder, slot)
            SynthworksMenuHolder.Type.ITEM_LIST -> if (slot == 49) openItemList(player) else {
                val id = if (slot in 0..44) (holder.data["ids"] as? List<String>)?.getOrNull(slot) else null
                if (id != null) openItemDetail(player, id)
            }
            SynthworksMenuHolder.Type.ITEM_DETAIL -> when (slot) {
                11 -> openItemEditor(player, holder.context)
                13 -> holder.context?.let(plugin.itemManager::create)?.let { item ->
                    giveTo(player, item)
                    player.sendMessage("§aYou received ${item.amount}x ${holder.context}.")
                }
                15 -> deleteItem(player, holder.context)
                22 -> openItemList(player, holder.data["category"] as? String ?: "misc")
            }
            SynthworksMenuHolder.Type.RECIPE_CATEGORIES -> if (slot == 49) openEditor(player) else {
                val category = if (slot in 0..44) (holder.data["categories"] as? List<String>)?.getOrNull(slot) else null
                if (category != null) openRecipeListAdmin(player, category)
            }
            SynthworksMenuHolder.Type.RECIPE_LIST_ADMIN -> if (slot == 49) openRecipeCategories(player) else {
                val id = if (slot in 0..44) (holder.data["ids"] as? List<String>)?.getOrNull(slot) else null
                if (id != null) openRecipeDetail(player, id)
            }
            SynthworksMenuHolder.Type.RECIPE_OUTPUT_PICKER -> {
                if (slot == 49) {
                    val parent = holder.data["parent"] as? SynthworksMenuHolder
                    if (parent != null) {
                        parent.data["openingPicker"] = false
                        holder.data["returningToParent"] = true
                        player.openInventory(parent.menu)
                    } else openEditor(player)
                } else {
                    val ids = holder.data["ids"] as? List<String> ?: emptyList()
                    val id = if (slot in 0..44) ids.getOrNull(slot) else null
                    val parent = holder.data["parent"] as? SynthworksMenuHolder
                    if (id != null && parent != null) {
                        plugin.itemManager.create(id)?.let { parent.menu.setItem(13, it) }
                        parent.data["openingPicker"] = false
                        holder.data["returningToParent"] = true
                        player.openInventory(parent.menu)
                    }
                }
            }
            SynthworksMenuHolder.Type.RECIPE_DETAIL -> when (slot) {
                11 -> openRecipeEditor(player, holder.context)
                13 -> holder.context?.let(plugin.recipeManager::recipe)?.let { recipe ->
                    plugin.itemManager.create(recipe.resultItemId)?.let { giveTo(player, it) }
                }
                15 -> holder.context?.let(plugin.recipeManager::recipe)?.let { giveTo(player, plugin.blueprintManager.create(it)) }
                22 -> deleteRecipe(player, holder.context)
                26 -> openRecipeListAdmin(player, holder.data["category"] as? String ?: "misc")
            }
            SynthworksMenuHolder.Type.RECIPE_EDITOR -> onRecipeEditorClick(player, holder, slot)
        }
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        val holder = event.view.topInventory.holder as? SynthworksMenuHolder ?: return
        val allowedTopSlots = when (holder.type) {
            SynthworksMenuHolder.Type.CRAFT -> inputSlots
            SynthworksMenuHolder.Type.ITEM_EDITOR -> listOf(13)
            SynthworksMenuHolder.Type.RECIPE_EDITOR -> recipeEditorSlots
            else -> emptyList()
        }
        if (event.rawSlots.any { it < event.view.topInventory.size && it !in allowedTopSlots }) event.isCancelled = true
    }

    @EventHandler
    fun onClose(event: InventoryCloseEvent) {
        val holder = event.inventory.holder as? SynthworksMenuHolder ?: return
        val player = event.player as? Player ?: return
        if (prompts[player.uniqueId]?.holder === holder) return
        if (holder.type == SynthworksMenuHolder.Type.CRAFT) {
            returnIngredients(player, event.inventory)
        } else if (holder.type == SynthworksMenuHolder.Type.ITEM_EDITOR && holder.context == null) {
            returnEditorItem(player, event.inventory)
        } else if (holder.type == SynthworksMenuHolder.Type.RECIPE_EDITOR && holder.data["openingPicker"] != true) {
            returnRecipeIngredients(player, event.inventory)
        } else if (holder.type == SynthworksMenuHolder.Type.RECIPE_OUTPUT_PICKER && holder.data["returningToParent"] != true) {
            val parent = holder.data["parent"] as? SynthworksMenuHolder ?: return
            parent.data["openingPicker"] = false
            returnRecipeIngredients(player, parent.menu)
        }
        if (holder.type == SynthworksMenuHolder.Type.ITEM_EDITOR) itemDrafts.remove(player.uniqueId)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        prompts.remove(event.player.uniqueId)
        itemDrafts.remove(event.player.uniqueId)
    }

    @EventHandler
    fun onChat(event: AsyncPlayerChatEvent) {
        val prompt = prompts.remove(event.player.uniqueId) ?: return
        event.isCancelled = true
        val input = event.message.trim()
        if (input.equals("cancel", ignoreCase = true)) {
            Bukkit.getScheduler().runTask(plugin, Runnable {
                event.player.sendMessage("§eEditor prompt cancelled.")
                if (event.player.isOnline) event.player.openInventory(prompt.holder.menu)
            })
            return
        }
        Bukkit.getScheduler().runTask(plugin, Runnable {
            when (prompt.action) {
                "item-id" -> {
                    val parts = input.split(Regex("\\s+")).filter { it.isNotBlank() }
                    val validParts = parts.size == 2 && parts.all { it.matches(Regex("[a-zA-Z0-9_-]+")) }
                    if (!validParts) {
                        event.player.sendMessage("§cUse exactly two parts: folder item_id (letters, numbers, _ and - only).")
                        event.player.openInventory(prompt.holder.menu)
                    } else {
                        saveItem(event.player, prompt.holder, "${parts[0]}_${parts[1]}")
                    }
                }
                "item-name" -> updateTemplate(event.player, prompt.holder, input, false)
                "item-lore" -> updateTemplate(event.player, prompt.holder, input, true)
                "item-durability" -> updateItemNumber(event.player, prompt.holder, input, false)
                "item-max-stack" -> updateItemNumber(event.player, prompt.holder, input, true)
                "item-attributes" -> updateAttributes(event.player, prompt.holder, input)
                "recipe-id" -> saveRecipe(event.player, prompt.holder, input)
                "recipe-category" -> updateRecipeCategory(event.player, prompt.holder, input)
            }
        })
    }

    private fun selectRecipe(player: Player, recipe: SynthworksRecipe) {
        if (recipe.requiresBlueprint && !plugin.blueprintManager.isUnlocked(player, recipe.id)) {
            player.sendMessage("§cYou need the blueprint for this recipe.")
            return
        }
        openCraft(player, recipe, fromBlueprintStorage = false)
    }

    private fun handleIngredientClick(event: InventoryClickEvent, inventory: org.bukkit.inventory.Inventory, slot: Int) {
        event.isCancelled = true
        if (event.click != org.bukkit.event.inventory.ClickType.LEFT && event.click != org.bukkit.event.inventory.ClickType.RIGHT) return
        val cursor = event.cursor?.takeUnless { it.type.isAir }?.clone()
        val current = inventory.getItem(slot)?.takeUnless { it.type.isAir }
        if (cursor != null) {
            if (current == null) {
                val placed = cursor.clone()
                if (event.click == org.bukkit.event.inventory.ClickType.RIGHT && placed.amount > 1) placed.amount = 1
                inventory.setItem(slot, placed)
                cursor.amount -= placed.amount
                setCursor(event, cursor.takeUnless { it.amount <= 0 })
            } else if (current.isSimilar(cursor)) {
                val count = if (event.click == org.bukkit.event.inventory.ClickType.RIGHT) 1 else cursor.amount
                val moved = count.coerceAtMost(current.maxStackSize - current.amount)
                if (moved > 0) {
                    current.amount += moved
                    cursor.amount -= moved
                    setCursor(event, cursor.takeUnless { it.amount <= 0 })
                }
            }
        } else if (current != null) {
            if (event.click == org.bukkit.event.inventory.ClickType.RIGHT && current.amount > 1) {
                current.amount -= 1
                setCursor(event, current.clone().apply { amount = 1 })
            } else {
                inventory.setItem(slot, null)
                setCursor(event, current)
            }
        }
    }

    private fun confirmCraft(player: Player, holder: SynthworksMenuHolder) {
        val recipe = holder.context?.let(plugin.recipeManager::recipe)
        if (recipe == null) {
            player.sendMessage("§cThis recipe no longer exists.")
            player.closeInventory()
            return
        }
        if (recipe.requiresBlueprint && !plugin.blueprintManager.isUnlocked(player, recipe.id)) {
            player.sendMessage("§cYou need the blueprint for this recipe.")
            player.closeInventory()
            return
        }
        val output = plugin.itemManager.create(recipe.resultItemId)
        if (output == null) {
            player.sendMessage("§cThe recipe output item is unavailable.")
            return
        }
        val inventory = holder.menu
        val matches = recipe.ingredients.indices.all { index ->
            val supplied = inventory.getItem(inputSlots[index])
            val required = recipe.ingredients[index]
            supplied != null && supplied.isSimilar(required) && supplied.amount >= required.amount
        }
        if (!matches) {
            player.sendMessage("§cIngredients do not match or are insufficient; all submitted items were returned.")
            returnIngredients(player, inventory)
            player.closeInventory()
            return
        }

        try {
            plugin.blueprintManager.recordManualCraft(player, recipe)
        } catch (exception: IOException) {
            plugin.logger.severe("Could not save manual craft progress for ${player.uniqueId}: ${exception.message}")
            player.sendMessage("§cCould not save recipe progress. The craft was not completed.")
            return
        }
        recipe.ingredients.forEachIndexed { index, ingredient ->
            val supplied = inventory.getItem(inputSlots[index])!!
            supplied.amount -= ingredient.amount
            if (supplied.amount <= 0) inventory.setItem(inputSlots[index], null)
        }
        giveTo(player, output)
        player.sendMessage("§aSynthworks crafting complete: ${recipe.id}")
        player.closeInventory()
    }

    private fun quickCraft(player: Player, holder: SynthworksMenuHolder) {
        val recipe = holder.context?.let(plugin.recipeManager::recipe)
        if (recipe == null) {
            player.sendMessage("§cThis recipe no longer exists.")
            return
        }
        if (recipe.requiresBlueprint && !plugin.blueprintManager.isUnlocked(player, recipe.id)) {
            player.sendMessage("§cYou need the blueprint for this recipe.")
            return
        }
        if (!plugin.blueprintManager.canQuickCraft(player, recipe)) {
            player.sendMessage("§eCraft this recipe manually once after unlocking its blueprint to enable Quick Craft.")
            return
        }
        val output = plugin.itemManager.create(recipe.resultItemId)
        if (output == null) {
            player.sendMessage("§cThe recipe output item is unavailable.")
            return
        }

        val requirements = mutableListOf<Pair<ItemStack, Int>>()
        recipe.ingredients.forEach { ingredient ->
            val existing = requirements.indexOfFirst { (sample, _) -> sample.isSimilar(ingredient) }
            if (existing < 0) requirements += ingredient.clone() to ingredient.amount
            else {
                val (sample, amount) = requirements[existing]
                requirements[existing] = sample to (amount + ingredient.amount)
            }
        }
        val storage = player.inventory.storageContents
        val insufficient = requirements.firstOrNull { (required, amount) ->
            storage.filterNotNull().filter { it.isSimilar(required) }.sumOf { it.amount } < amount
        }
        if (insufficient != null) {
            player.sendMessage("§cNot enough ${insufficient.first.type.name.lowercase().replace('_', ' ')} in your inventory.")
            return
        }

        requirements.forEach { (required, amount) ->
            var remaining = amount
            for (slot in storage.indices) {
                val supplied = player.inventory.getItem(slot) ?: continue
                if (!supplied.isSimilar(required)) continue
                val consumed = minOf(remaining, supplied.amount)
                supplied.amount -= consumed
                if (supplied.amount <= 0) player.inventory.setItem(slot, null)
                remaining -= consumed
                if (remaining == 0) break
            }
        }
        giveTo(player, output)
        player.sendMessage("§aQuick crafted: ${recipeDisplayName(recipe)}")
    }

    private fun returnIngredients(player: Player, inventory: org.bukkit.inventory.Inventory) {
        inputSlots.forEach { slot ->
            val item = inventory.getItem(slot)?.takeUnless { it.type.isAir } ?: return@forEach
            inventory.setItem(slot, null)
            player.inventory.addItem(item).values.forEach { player.world.dropItemNaturally(player.location, it) }
        }
    }

    private fun returnRecipeIngredients(player: Player, inventory: org.bukkit.inventory.Inventory) {
        val holder = inventory.holder as? SynthworksMenuHolder ?: return
        @Suppress("UNCHECKED_CAST")
        val borrowed = holder.data["borrowedIngredients"] as? MutableSet<Int> ?: emptySet<Int>()
        @Suppress("UNCHECKED_CAST")
        val baseline = holder.data["baselineIngredients"] as? MutableMap<Int, ItemStack> ?: emptyMap<Int, ItemStack>()
        recipeEditorSlots.forEach { slot ->
            val item = inventory.getItem(slot)?.takeUnless { it.type.isAir } ?: return@forEach
            inventory.setItem(slot, null)
            if (slot in borrowed) {
                val original = baseline[slot]
                val returned = if (original != null && item.isSimilar(original)) {
                    item.clone().apply { amount = (item.amount - original.amount).coerceAtLeast(0) }
                        .takeUnless { it.amount == 0 }
                } else item
                if (returned != null) {
                    player.inventory.addItem(returned).values.forEach { player.world.dropItemNaturally(player.location, it) }
                }
            }
        }
    }

    private fun returnEditorItem(player: Player, inventory: org.bukkit.inventory.Inventory) {
        val item = inventory.getItem(13)?.takeUnless { it.type.isAir || it.type == Material.YELLOW_STAINED_GLASS_PANE } ?: return
        inventory.setItem(13, null)
        player.inventory.addItem(item).values.forEach { player.world.dropItemNaturally(player.location, it) }
    }

    private fun handleItemTemplateClick(event: InventoryClickEvent, holder: SynthworksMenuHolder) {
        event.isCancelled = true
        if (event.click != org.bukkit.event.inventory.ClickType.LEFT && event.click != org.bukkit.event.inventory.ClickType.RIGHT) return
        val cursor = event.cursor?.takeUnless { it.type.isAir }?.clone()
        val current = holder.menu.getItem(13)?.takeUnless {
            it.type.isAir || (holder.context == null && it.type == Material.YELLOW_STAINED_GLASS_PANE)
        }
        if (cursor != null) {
            if (current == null) {
                val placed = cursor.clone()
                if (event.click == org.bukkit.event.inventory.ClickType.RIGHT && placed.amount > 1) placed.amount = 1
                holder.menu.setItem(13, placed)
                cursor.amount -= placed.amount
                setCursor(event, cursor.takeUnless { it.amount <= 0 })
            } else if (current.isSimilar(cursor)) {
                val requested = if (event.click == org.bukkit.event.inventory.ClickType.RIGHT) 1 else cursor.amount
                val moved = requested.coerceAtMost(current.maxStackSize - current.amount)
                if (moved > 0) {
                    current.amount += moved
                    cursor.amount -= moved
                    setCursor(event, cursor.takeUnless { it.amount <= 0 })
                }
            } else {
                holder.menu.setItem(13, cursor)
                setCursor(event, current)
            }
        } else if (current != null) {
            if (event.click == org.bukkit.event.inventory.ClickType.RIGHT && current.amount > 1) {
                val taken = current.clone().apply { amount = (current.amount + 1) / 2 }
                current.amount -= taken.amount
                setCursor(event, taken)
            } else {
                holder.menu.setItem(13, null)
                setCursor(event, current)
            }
        }
    }

    private fun handleRecipeEditorIngredientClick(event: InventoryClickEvent, holder: SynthworksMenuHolder, slot: Int) {
        event.isCancelled = true
        if (event.click != org.bukkit.event.inventory.ClickType.LEFT && event.click != org.bukkit.event.inventory.ClickType.RIGHT) return
        val cursor = event.cursor?.takeUnless { it.type.isAir }?.clone()
        val current = holder.menu.getItem(slot)?.takeUnless { it.type.isAir }
        @Suppress("UNCHECKED_CAST")
        val borrowed = holder.data["borrowedIngredients"] as MutableSet<Int>
        @Suppress("UNCHECKED_CAST")
        val baseline = holder.data["baselineIngredients"] as MutableMap<Int, ItemStack>

        if (cursor != null) {
            if (current == null) {
                val placed = cursor.clone()
                if (event.click == org.bukkit.event.inventory.ClickType.RIGHT && placed.amount > 1) placed.amount = 1
                holder.menu.setItem(slot, placed)
                borrowed.add(slot)
                cursor.amount -= placed.amount
                setCursor(event, cursor.takeUnless { it.amount <= 0 })
            } else if (current.isSimilar(cursor)) {
                val requested = if (event.click == org.bukkit.event.inventory.ClickType.RIGHT) 1 else cursor.amount
                val moved = requested.coerceAtMost(current.maxStackSize - current.amount)
                if (moved > 0) {
                    current.amount += moved
                    borrowed.add(slot)
                    cursor.amount -= moved
                    setCursor(event, cursor.takeUnless { it.amount <= 0 })
                }
            }
            return
        }

        if (current == null) return
        if (slot in borrowed) {
            val original = baseline[slot]
            val protectedAmount = if (original != null && current.isSimilar(original)) original.amount else 0
            val available = current.amount - protectedAmount
            if (available <= 0) {
                borrowed.remove(slot)
            } else if (event.click == org.bukkit.event.inventory.ClickType.RIGHT) {
                current.amount -= 1
                setCursor(event, current.clone().apply { amount = 1 })
                if (current.amount <= protectedAmount) {
                    borrowed.remove(slot)
                    if (current.amount <= 0) holder.menu.setItem(slot, null)
                }
            } else {
                setCursor(event, current.clone().apply { amount = available })
                current.amount -= available
                borrowed.remove(slot)
                if (current.amount <= 0) holder.menu.setItem(slot, null)
            }
        } else {
            holder.menu.setItem(slot, null)
            baseline.remove(slot)
        }
    }

    private fun onItemEditorClick(player: Player, holder: SynthworksMenuHolder, slot: Int) {
        val draft = itemDrafts[player.uniqueId] ?: return
        when (slot) {
            10 -> { draft.blockPlacement = !draft.blockPlacement; refreshItemEditor(player, holder) }
            11 -> { draft.vanillaCrafting = !draft.vanillaCrafting; refreshItemEditor(player, holder) }
            12 -> { draft.glint = !draft.glint; refreshItemEditor(player, holder) }
            14 -> { draft.unbreakable = !draft.unbreakable; refreshItemEditor(player, holder) }
            15 -> ask(player, holder, "item-durability", "Enter durability damage as a non-negative number:")
            16 -> ask(player, holder, "item-attributes", "Enter attributes as ATTRIBUTE:amount:operation,... or - to clear:")
            20 -> ask(player, holder, "item-name", "Enter the item name in chat (use & color codes):")
            22 -> ask(player, holder, "item-lore", "Enter lore lines separated by | (or - for no lore):")
            24 -> ask(player, holder, "item-max-stack", "Enter max stack size (1-99), or 0 for material default:")
            18 -> openEditor(player)
            26 -> {
                if (holder.menu.getItem(13)?.type?.isAir != false || holder.menu.getItem(13)?.type == Material.YELLOW_STAINED_GLASS_PANE) {
                    player.sendMessage("§cPlace an item template in the center slot first.")
                    return
                }
                if (draft.id == null) ask(player, holder, "item-id", "Nhập ID theo mẫu: folder item_id (ví dụ: dtier copper_wire)")
                else saveItem(player, holder, draft.id!!)
            }
        }
    }

    private fun updateTemplate(player: Player, holder: SynthworksMenuHolder, value: String, isLore: Boolean) {
        val item = holder.menu.getItem(13) ?: return
        val meta = item.itemMeta
        if (isLore) {
            meta.lore(if (value == "-") emptyList() else value.split('|').map(plugin.itemManager.legacy::deserialize))
        } else {
            meta.displayName(plugin.itemManager.legacy.deserialize(value))
        }
        item.itemMeta = meta
        holder.menu.setItem(13, item)
    }

    private fun updateItemNumber(player: Player, holder: SynthworksMenuHolder, value: String, maxStack: Boolean) {
        val number = value.toIntOrNull()
        val draft = itemDrafts[player.uniqueId] ?: return
        if (number == null || (maxStack && number !in 0..99) || (!maxStack && number < 0)) {
            player.sendMessage("§cInvalid number.")
            return
        }
        if (maxStack) draft.maxStackSize = number.takeIf { it > 0 }
        else draft.durability = number
        refreshItemEditor(player, holder)
    }

    private fun updateAttributes(player: Player, holder: SynthworksMenuHolder, value: String) {
        val draft = itemDrafts[player.uniqueId] ?: return
        if (value == "-") {
            draft.attributes.clear()
        } else {
            val parsed = linkedMapOf<String, AttributeDefinition>()
            val invalid = value.split(',').firstNotNullOfOrNull { entry ->
                val parts = entry.trim().split(':')
                val amount = parts.getOrNull(1)?.toDoubleOrNull()
                if (parts.size != 3 || amount == null) entry.trim()
                else {
                    parsed[parts[0].uppercase()] = AttributeDefinition(amount, parts[2].uppercase())
                    null
                }
            }
            if (invalid != null) {
                player.sendMessage("§cInvalid attribute entry: $invalid")
                return
            }
            draft.attributes = parsed
        }
        player.sendMessage("§aUpdated ${draft.attributes.size} attribute(s).")
    }

    private fun saveItem(player: Player, holder: SynthworksMenuHolder, id: String) {
        val draft = itemDrafts[player.uniqueId] ?: return
        val template = holder.menu.getItem(13)?.takeUnless { it.type.isAir || it.type == Material.YELLOW_STAINED_GLASS_PANE }
        if (template == null) {
            player.sendMessage("§cPlace an item template in the center slot first.")
            return
        }
        try {
            val meta = template.itemMeta
            meta.isUnbreakable = draft.unbreakable
            if (draft.glint && !meta.hasEnchants()) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true)
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS)
            }
            (meta as? Damageable)?.damage = draft.durability
            template.itemMeta = meta
            plugin.itemManager.saveFromStack(
                id,
                template,
                draft.blockPlacement,
                draft.vanillaCrafting,
                draft.glint,
                draft.unbreakable,
                draft.durability,
                draft.maxStackSize,
                draft.attributes
            )
            itemDrafts.remove(player.uniqueId)
            player.sendMessage("§aSaved custom item '$id'.")
            openItemList(player)
        } catch (exception: Exception) {
            player.sendMessage("§cCould not save item: ${exception.message}")
        }
    }

    private fun onRecipeEditorClick(player: Player, holder: SynthworksMenuHolder, slot: Int) {
        when (slot) {
            22 -> openRecipeOutputPicker(player, holder)
            47 -> {
                val enabled = !(holder.data["requiresBlueprint"] as Boolean)
                holder.data["requiresBlueprint"] = enabled
                holder.menu.setItem(47, toggle(Material.PAPER, "Requires blueprint", enabled))
            }
            51 -> ask(player, holder, "recipe-category", "Enter a category name (for example dtier or refined):")
            craftConfirmSlot -> {
                val result = plugin.itemManager.itemId(holder.menu.getItem(13))
                if (result == null) {
                    player.sendMessage("§cThe output slot must contain a Synthworks custom item.")
                    return
                }
                val ingredients = recipeEditorSlots.mapNotNull { holder.menu.getItem(it)?.clone() }
                if (ingredients.isEmpty()) {
                    player.sendMessage("§cAdd at least one ingredient.")
                    return
                }
                if (holder.context == null) ask(player, holder, "recipe-id", "Enter recipe ID as: folder recipe_name (example: ctier tactical_vest)")
                else saveRecipe(player, holder, "${holder.context} ${holder.data["category"]}")
            }
            craftBackSlot -> openEditor(player)
        }
    }

    private fun saveRecipe(player: Player, holder: SynthworksMenuHolder, input: String) {
        val parsed = input.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        val id: String
        val category: String
        if (holder.context == null) {
            if (parsed.size != 2 || parsed.any { !it.matches(Regex("[a-zA-Z0-9_-]+")) }) {
                player.sendMessage("§cUse exactly two parts: folder recipe_name.")
                player.openInventory(holder.menu)
                return
            }
            category = parsed[0].lowercase()
            id = "${category}_${parsed[1].lowercase()}"
        } else {
            id = holder.context!!.lowercase()
            category = holder.data["category"] as? String ?: "misc"
        }
        val result = plugin.itemManager.itemId(holder.menu.getItem(13))
        if (!id.matches(Regex("[a-z0-9_-]+")) || result == null) {
            player.sendMessage("§cInvalid recipe ID or missing custom output item.")
            return
        }
        val ingredients = recipeEditorSlots.mapNotNull { holder.menu.getItem(it)?.takeUnless { item -> item.type.isAir }?.clone() }
        if (ingredients.isEmpty()) {
            player.sendMessage("§cAdd at least one ingredient.")
            return
        }
        try {
            plugin.recipeManager.save(
                SynthworksRecipe(id, category, result, ingredients, holder.data["requiresBlueprint"] as Boolean)
            )
            player.sendMessage("§aSaved recipe '$id'.")
            openRecipeListAdmin(player, category)
        } catch (exception: IOException) {
            player.sendMessage("§cCould not save recipe: ${exception.message}")
        }
    }

    private fun updateRecipeCategory(player: Player, holder: SynthworksMenuHolder, value: String) {
        val category = value.lowercase()
        if (!category.matches(Regex("[a-z0-9_-]+"))) {
            player.sendMessage("§cCategory can contain only letters, numbers, underscores, and hyphens.")
            player.openInventory(holder.menu)
            return
        }
        holder.data["category"] = category
        holder.menu.setItem(51, button(Material.CHEST, "&eCategory: $category", "&7Click to change"))
        player.sendMessage("§aRecipe category set to '$category'.")
        player.openInventory(holder.menu)
    }

    private fun openRecipeOutputPicker(player: Player, parent: SynthworksMenuHolder) {
        parent.data["openingPicker"] = true
        val picker = menu(SynthworksMenuHolder.Type.RECIPE_OUTPUT_PICKER, 54, "&8Choose Recipe Output")
        val ids = plugin.itemManager.itemIds().sorted().take(45)
        picker.data["ids"] = ids
        picker.data["parent"] = parent
        ids.forEachIndexed { index, id ->
            plugin.itemManager.create(id)?.let { picker.menu.setItem(index, it) }
        }
        picker.menu.setItem(49, button(Material.RED_STAINED_GLASS_PANE, "&cBack"))
        player.openInventory(picker.menu)
    }

    private fun ask(player: Player, holder: SynthworksMenuHolder, action: String, message: String) {
        prompts[player.uniqueId] = Prompt(holder, action)
        player.closeInventory()
        player.sendMessage("§e$message")
        player.sendMessage("§7Inventory đã đóng. Hãy nhập câu trả lời trong chat; gõ cancel để hủy.")
    }

    private fun giveTo(player: Player, stack: ItemStack) {
        player.inventory.addItem(stack).values.forEach { player.world.dropItemNaturally(player.location, it) }
    }

    private fun setCursor(event: InventoryClickEvent, item: ItemStack?) {
        event.whoClicked.setItemOnCursor(item ?: ItemStack(Material.AIR))
    }

    private fun deleteItem(player: Player, id: String?) {
        if (id == null) return
        try {
            plugin.itemManager.delete(id)
            player.sendMessage("§aDeleted item '$id'.")
            openItemList(player)
        } catch (exception: IOException) {
            player.sendMessage("§cCould not delete item: ${exception.message}")
        }
    }

    private fun deleteRecipe(player: Player, id: String?) {
        if (id == null) return
        val category = plugin.recipeManager.recipe(id)?.category
        try {
            plugin.recipeManager.delete(id)
            player.sendMessage("§aDeleted recipe '$id'.")
            if (category != null) openRecipeListAdmin(player, category) else openRecipeCategories(player)
        } catch (exception: IOException) {
            player.sendMessage("§cCould not delete recipe: ${exception.message}")
        }
    }

    private fun String.displayName(): String = split('-', '_').joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

    private fun recipeDisplayName(recipe: SynthworksRecipe): String =
        plugin.itemManager.definition(recipe.resultItemId)?.name ?: recipe.resultItemId

    private fun itemCategory(id: String): String =
        id.substringBefore('_').takeIf { '_' in id } ?: "misc"

    private data class ItemDraft(
        var id: String?,
        var blockPlacement: Boolean,
        var vanillaCrafting: Boolean,
        var glint: Boolean,
        var unbreakable: Boolean,
        var durability: Int,
        var maxStackSize: Int?,
        var attributes: MutableMap<String, AttributeDefinition>
    )

    private data class Prompt(val holder: SynthworksMenuHolder, val action: String)
}
