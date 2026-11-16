package vn.extractionfantasy.synthworks.gui

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

class SynthworksMenuHolder(
    val type: Type,
    var context: String? = null
) : InventoryHolder {
    lateinit var menu: Inventory
    val data = mutableMapOf<String, Any>()

    override fun getInventory(): Inventory = menu

    enum class Type {
        MAIN,
        CATEGORIES,
        RECIPE_LIST,
        BLUEPRINTS,
        CRAFT,
        EDITOR_HOME,
        ITEM_EDITOR,
        ITEM_CATEGORIES,
        ITEM_LIST,
        ITEM_DETAIL,
        RECIPE_EDITOR,
        RECIPE_OUTPUT_PICKER,
        RECIPE_CATEGORIES,
        RECIPE_LIST_ADMIN,
        RECIPE_DETAIL
    }
}
