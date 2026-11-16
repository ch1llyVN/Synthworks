package vn.extractionfantasy.synthworks.item

import org.bukkit.Material

data class ItemDefinition(
    val id: String,
    val material: Material,
    val name: String,
    val lore: List<String>,
    val customModelData: Int?,
    val amount: Int,
    val maxStackSize: Int?,
    val unbreakable: Boolean,
    val glint: Boolean,
    val blockPlacement: Boolean,
    val vanillaCrafting: Boolean,
    val durability: Int,
    val attributes: Map<String, AttributeDefinition>,
    val serializedStack: String?
)

data class AttributeDefinition(
    val amount: Double,
    val operation: String
)
