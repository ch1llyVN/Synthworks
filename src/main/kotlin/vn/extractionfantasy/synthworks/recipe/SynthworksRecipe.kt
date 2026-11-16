package vn.extractionfantasy.synthworks.recipe

import org.bukkit.inventory.ItemStack

data class SynthworksRecipe(
    val id: String,
    val category: String,
    val resultItemId: String,
    val ingredients: List<ItemStack>,
    val requiresBlueprint: Boolean
)
