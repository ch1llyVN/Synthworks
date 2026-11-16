package vn.extractionfantasy.synthworks.item

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.inventory.EquipmentSlotGroup
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.persistence.PersistentDataType
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import vn.extractionfantasy.synthworks.SynthworksPlugin
import java.io.IOException
import java.util.Base64
import java.util.Locale

class ItemManager(private val plugin: SynthworksPlugin) {
    private val definitions = linkedMapOf<String, ItemDefinition>()
    val legacy = LegacyComponentSerializer.legacyAmpersand()

    fun reload() {
        definitions.clear()
        val itemFiles = linkedMapOf<String, java.io.File>()
        val directory = plugin.dataFolder.resolve("custom_items")
        if (directory.isDirectory) {
            directory.walkTopDown()
                .filter { it.isFile && it.extension.equals("yml", true) }
                .sortedBy { it.path }
                .forEach { file ->
                    val id = file.nameWithoutExtension.lowercase(Locale.ROOT)
                    if (itemFiles.putIfAbsent(id, file) != null) {
                        plugin.logger.warning("Duplicate custom item ID '$id' found at '${file.path}'.")
                    }
                }
        }

        val legacyFile = plugin.dataFolder.resolve("custom_items.yml")
        val legacyYaml = YamlConfiguration.loadConfiguration(legacyFile)
        val legacyRoot = legacyYaml.getConfigurationSection("items")
        legacyRoot?.getKeys(false)?.forEach { rawId ->
            val id = rawId.lowercase(Locale.ROOT)
            if (!id.matches(Regex("[a-z0-9_:-]+"))) {
                plugin.logger.warning("Skipping legacy item '$rawId': invalid ID.")
                return@forEach
            }
            val target = itemFile(id)
            if (id !in itemFiles) {
                val section = legacyRoot.getConfigurationSection(rawId) ?: return@forEach
                val migrated = sectionToYaml(section)
                if (!target.parentFile.exists() && !target.parentFile.mkdirs()) {
                    plugin.logger.severe("Could not create custom item folder for '$id'.")
                    return@forEach
                }
                try {
                    migrated.save(target)
                    itemFiles[id] = target
                    legacyYaml.set("items.$rawId", null)
                } catch (exception: IOException) {
                    plugin.logger.severe("Could not migrate custom item '$id': ${exception.message}")
                }
            } else {
                legacyYaml.set("items.$rawId", null)
            }
        }
        if (legacyRoot != null && legacyYaml.getConfigurationSection("items")?.getKeys(false).isNullOrEmpty()) {
            legacyYaml.set("items", null)
        }
        if (legacyFile.isFile && legacyYaml.getConfigurationSection("items") == null) {
            try {
                legacyYaml.save(legacyFile)
            } catch (exception: IOException) {
                plugin.logger.severe("Could not finish migrating custom_items.yml: ${exception.message}")
            }
        }

        for ((id, file) in itemFiles) {
            val section = YamlConfiguration.loadConfiguration(file)
            loadDefinition(id, section)
        }
    }

    private fun loadDefinition(rawId: String, section: ConfigurationSection) {
            val id = rawId.lowercase(Locale.ROOT)
            val materialName = section.getString("material") ?: "PAPER"
            val material = Material.matchMaterial(materialName)
            if (material == null || material.isAir) {
                plugin.logger.warning("Skipping item '$id': invalid material '$materialName'.")
                return
            }

            val attributes = linkedMapOf<String, AttributeDefinition>()
            val attrSection = section.getConfigurationSection("attributes")
            attrSection?.getKeys(false)?.forEach { key ->
                val attr = attrSection.getConfigurationSection(key) ?: return@forEach
                attributes[key.uppercase(Locale.ROOT)] = AttributeDefinition(
                    amount = attr.getDouble("amount"),
                    operation = attr.getString("operation", "ADD_NUMBER")!!.uppercase(Locale.ROOT)
                )
            }

            val cleanId = id.lowercase(Locale.ROOT)
            if (!cleanId.matches(Regex("[a-z0-9_:-]+"))) {
                plugin.logger.warning("Skipping item '$id': IDs may contain only lowercase letters, digits, _, -, and :.")
                return
            }

            val serializedStack = section.getString("serialized-stack")
            if (serializedStack != null) {
                try {
                    val decoded = ItemStack.deserializeBytes(Base64.getDecoder().decode(serializedStack))
                    if (decoded.type.isAir) {
                        plugin.logger.warning("Skipping item '$id': serialized stack is AIR.")
                        return
                    }
                } catch (exception: IllegalArgumentException) {
                    plugin.logger.warning("Skipping item '$id': invalid serialized stack: ${exception.message}")
                    return
                } catch (exception: RuntimeException) {
                    plugin.logger.warning("Skipping item '$id': could not deserialize serialized stack: ${exception.message}")
                    return
                }
            }

            definitions[cleanId] = ItemDefinition(
                id = cleanId,
                material = material,
                name = section.getString("name", cleanId)!!,
                lore = section.getStringList("lore"),
                customModelData = if (section.contains("custom-model-data")) section.getInt("custom-model-data") else null,
                amount = section.getInt("amount", 1).coerceIn(1, material.maxStackSize),
                maxStackSize = if (section.contains("max-stack-size")) section.getInt("max-stack-size").coerceIn(1, 99) else null,
                unbreakable = section.getBoolean("unbreakable", false),
                glint = section.getBoolean("glint", false),
                blockPlacement = section.getBoolean("block-placement", false),
                vanillaCrafting = section.getBoolean("vanilla-crafting", false),
                durability = section.getInt("durability", 0).coerceAtLeast(0),
                attributes = attributes,
                serializedStack = serializedStack
            )
    }

    private fun sectionToYaml(section: ConfigurationSection): YamlConfiguration =
        YamlConfiguration().also { yaml ->
            section.getKeys(true).filterNot { section.isConfigurationSection(it) }.forEach { key ->
                yaml.set(key, section.get(key))
            }
        }

    private fun itemFile(id: String): java.io.File {
        val cleanId = id.lowercase(Locale.ROOT)
        require(cleanId.matches(Regex("[a-z0-9_:-]+"))) { "Invalid custom item ID: $id" }
        val category = cleanId.substringBefore('_').takeIf { '_' in cleanId } ?: "misc"
        require(category.matches(Regex("[a-z0-9-]+"))) { "Invalid custom item category: $category" }
        return plugin.dataFolder.resolve("custom_items").resolve(category).resolve("$cleanId.yml")
    }

    fun itemIds(): Set<String> = definitions.keys.toSet()

    fun definition(id: String): ItemDefinition? = definitions[id.lowercase(Locale.ROOT)]

    fun create(id: String, requestedAmount: Int? = null): ItemStack? {
        val def = definition(id) ?: return null
        val stack = if (def.serializedStack != null) {
            try {
                ItemStack.deserializeBytes(Base64.getDecoder().decode(def.serializedStack))
            } catch (exception: RuntimeException) {
                plugin.logger.severe("Could not deserialize saved item '${def.id}': ${exception.message}")
                return null
            }
        } else {
            ItemStack(def.material)
        }
        val meta = stack.itemMeta

        if (def.serializedStack == null) {
            meta.displayName(legacy.deserialize(def.name))
            if (def.lore.isNotEmpty()) meta.lore(def.lore.map { legacy.deserialize(it) })
            def.customModelData?.let(meta::setCustomModelData)
            meta.isUnbreakable = def.unbreakable
            if (def.glint) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true)
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
            }

            if (def.maxStackSize != null) {
                try {
                    meta.setMaxStackSize(def.maxStackSize)
                } catch (ex: IllegalArgumentException) {
                    plugin.logger.warning("Could not set max stack size for '${def.id}': ${ex.message}")
                }
            }

            if (def.durability > 0 && meta is Damageable) {
                meta.damage = def.durability
            }

            applyAttributes(def, meta)
        }

        meta.persistentDataContainer.set(plugin.itemIdKey, PersistentDataType.STRING, def.id)
        meta.persistentDataContainer.set(plugin.markerKey, PersistentDataType.BYTE, 1.toByte())
        stack.itemMeta = meta
        stack.amount = (requestedAmount ?: def.amount).coerceIn(1, stack.maxStackSize)
        return stack
    }

    private fun applyAttributes(def: ItemDefinition, meta: org.bukkit.inventory.meta.ItemMeta) {
        applyAttributeDefinitions(def.id, def.attributes, meta)
    }

    private fun applyAttributeDefinitions(
        itemId: String,
        attributes: Map<String, AttributeDefinition>,
        meta: org.bukkit.inventory.meta.ItemMeta
    ) {
        for ((attributeName, definition) in attributes) {
            val attribute = runCatching { Attribute.valueOf(attributeName) }.getOrNull()
            val operation = runCatching { AttributeModifier.Operation.valueOf(definition.operation) }.getOrNull()
            if (attribute == null || operation == null) {
                plugin.logger.warning("Invalid attribute '${attributeName}' or operation '${definition.operation}' on '$itemId'.")
                continue
            }

            val key = NamespacedKey(plugin, "item_${itemId}_${attributeName.lowercase(Locale.ROOT)}")
            val modifier = AttributeModifier(key, definition.amount, operation, EquipmentSlotGroup.ANY)
            meta.getAttributeModifiers(attribute)?.filter { it.key == key }?.forEach {
                meta.removeAttributeModifier(attribute, it)
            }
            runCatching { meta.addAttributeModifier(attribute, modifier) }
                .onFailure { plugin.logger.warning("Could not apply $attributeName to '$itemId': ${it.message}") }
        }
    }

    fun isSynthworksItem(stack: ItemStack?): Boolean {
        if (stack == null || stack.type.isAir || !stack.hasItemMeta()) return false
        val data = stack.itemMeta.persistentDataContainer
        return data.has(plugin.markerKey, PersistentDataType.BYTE) ||
            data.has(plugin.legacyMarkerKey, PersistentDataType.BYTE)
    }

    fun itemId(stack: ItemStack?): String? {
        if (!isSynthworksItem(stack)) return null
        val data = stack!!.itemMeta.persistentDataContainer
        return data.get(plugin.itemIdKey, PersistentDataType.STRING)
            ?: data.get(plugin.legacyItemIdKey, PersistentDataType.STRING)
    }

    fun canPlace(stack: ItemStack?): Boolean {
        val id = itemId(stack) ?: return true
        return definition(id)?.blockPlacement ?: false
    }

    fun canUseInVanillaCrafting(stack: ItemStack?): Boolean {
        val id = itemId(stack) ?: return true
        return definition(id)?.vanillaCrafting ?: false
    }

    @Throws(IOException::class)
    fun saveFromStack(
        id: String,
        template: ItemStack,
        blockPlacement: Boolean,
        vanillaCrafting: Boolean,
        glint: Boolean,
        unbreakable: Boolean,
        durability: Int,
        maxStackSize: Int?,
        attributes: Map<String, AttributeDefinition>
    ) {
        val cleanId = id.lowercase(Locale.ROOT)
        require(cleanId.matches(Regex("[a-z0-9_:-]+"))) { "IDs may contain only lowercase letters, digits, _, -, and :." }
        require(!template.type.isAir) { "An item template is required." }

        val file = itemFile(cleanId)
        if (!file.parentFile.exists() && !file.parentFile.mkdirs()) {
            throw IOException("Could not create custom item folder '${file.parentFile}'.")
        }
        val yaml = YamlConfiguration()
        val meta = template.itemMeta
        val displayName = meta.displayName()
        yaml.set("material", template.type.name)
        yaml.set("name", displayName?.let(legacy::serialize) ?: cleanId)
        yaml.set("lore", meta.lore()?.map(legacy::serialize) ?: emptyList<String>())
        if (meta.hasCustomModelData()) yaml.set("custom-model-data", meta.customModelData)
        yaml.set("amount", template.amount)
        yaml.set("max-stack-size", maxStackSize)
        yaml.set("unbreakable", unbreakable)
        yaml.set("glint", glint)
        yaml.set("block-placement", blockPlacement)
        yaml.set("vanilla-crafting", vanillaCrafting)
        yaml.set("durability", durability.coerceAtLeast(0))
        yaml.set("attributes", attributes.mapValues { (_, definition) ->
            mapOf("amount" to definition.amount, "operation" to definition.operation)
        })
        if (maxStackSize != null) {
            try {
                meta.setMaxStackSize(maxStackSize)
            } catch (exception: IllegalArgumentException) {
                throw IllegalArgumentException("Invalid max stack size for '$cleanId': ${exception.message}", exception)
            }
        }
        applyAttributeDefinitions(cleanId, attributes, meta)
        template.itemMeta = meta
        yaml.set("serialized-stack", Base64.getEncoder().encodeToString(template.serializeAsBytes()))
        yaml.save(file)
        reload()
    }

    @Throws(IOException::class)
    fun delete(id: String) {
        val file = itemFile(id)
        if (file.exists() && !file.delete()) throw IOException("Could not delete custom item file '$file'.")
        val legacyFile = plugin.dataFolder.resolve("custom_items.yml")
        if (legacyFile.isFile) {
            val legacyYaml = YamlConfiguration.loadConfiguration(legacyFile)
            legacyYaml.set("items.${id.lowercase(Locale.ROOT)}", null)
            legacyYaml.save(legacyFile)
        }
        reload()
    }
}
