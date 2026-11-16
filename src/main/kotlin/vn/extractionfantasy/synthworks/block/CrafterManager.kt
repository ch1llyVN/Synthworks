package vn.extractionfantasy.synthworks.block

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.configuration.file.YamlConfiguration
import vn.extractionfantasy.synthworks.SynthworksPlugin
import java.io.IOException
import java.util.Locale
import kotlin.random.Random

class CrafterManager(private val plugin: SynthworksPlugin) {
    private val blocks = linkedSetOf<BlockPosition>()
    private val dataFile = plugin.dataFolder.resolve("crafters.yml")
    private val legacyDataFile = plugin.dataFolder.resolve("synthesizers.yml")
    private var cachedParticleName: String? = null
    private var cachedParticle: Particle = Particle.TRIAL_SPAWNER_DETECTION_OMINOUS

    fun load() {
        blocks.clear()
        val source = if (dataFile.isFile) dataFile else legacyDataFile
        val yaml = YamlConfiguration.loadConfiguration(source)
        val entries = yaml.getMapList("locations")
        for (entry in entries) {
            val world = entry["world"] as? String ?: continue
            val x = (entry["x"] as? Number)?.toInt() ?: continue
            val y = (entry["y"] as? Number)?.toInt() ?: continue
            val z = (entry["z"] as? Number)?.toInt() ?: continue
            blocks.add(BlockPosition(world, x, y, z))
        }
    }

    fun createItem(): ItemStack {
        val item = ItemStack(Material.CRAFTER)
        val meta = item.itemMeta
        meta.displayName(plugin.itemManager.legacy.deserialize("&dSynthworks Crafter"))
        meta.lore(
            listOf(
                plugin.itemManager.legacy.deserialize("&7A Synthworks crafting station"),
                plugin.itemManager.legacy.deserialize("&7Place it to bind this Crafter.")
            )
        )
        plugin.config.getInt("settings.crafter.custom-model-data")
            .takeIf { it > 0 }
            ?.let(meta::setCustomModelData)
        meta.persistentDataContainer.set(plugin.crafterItemKey, PersistentDataType.BYTE, 1)
        item.itemMeta = meta
        return item
    }

    fun isItem(item: ItemStack?): Boolean {
        if (item == null || item.type !in setOf(Material.CRAFTER, Material.SMITHING_TABLE) || !item.hasItemMeta()) return false
        val data = item.itemMeta.persistentDataContainer
        return data.has(plugin.crafterItemKey, PersistentDataType.BYTE) ||
            data.has(plugin.legacyCrafterItemKey, PersistentDataType.BYTE)
    }

    @Throws(IOException::class)
    fun register(location: Location) {
        val world = location.world ?: throw IOException("The Crafter world is unavailable.")
        val position = BlockPosition(world.name, location.blockX, location.blockY, location.blockZ)
        if (!blocks.add(position)) return
        try {
            save()
        } catch (exception: IOException) {
            blocks.remove(position)
            throw exception
        }
    }

    @Throws(IOException::class)
    fun unregister(location: Location) {
        val world = location.world ?: return
        val position = BlockPosition(world.name, location.blockX, location.blockY, location.blockZ)
        if (!blocks.remove(position)) return
        try {
            save()
        } catch (exception: IOException) {
            blocks.add(position)
            throw exception
        }
    }

    @Throws(IOException::class)
    fun unregisterAll(locations: Collection<Location>) {
        val positions = locations.mapNotNull { location ->
            location.world?.let { BlockPosition(it.name, location.blockX, location.blockY, location.blockZ) }
        }.filter { it in blocks }.toSet()
        if (positions.isEmpty()) return
        blocks.removeAll(positions)
        try {
            save()
        } catch (exception: IOException) {
            blocks.addAll(positions)
            throw exception
        }
    }

    fun isCrafter(location: Location): Boolean {
        val world = location.world ?: return false
        return BlockPosition(world.name, location.blockX, location.blockY, location.blockZ) in blocks
    }

    fun startParticles() {
        val interval = plugin.config.getLong("settings.crafter.particle-interval-ticks", 20L).coerceAtLeast(1L)
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable { emitParticles() }, interval, interval)
    }

    private fun emitParticles() {
        val particleCount = plugin.config.getInt("settings.crafter.particle-count", 12).coerceIn(1, 100)
        val range = plugin.config.getDouble("settings.crafter.particle-player-range", 24.0).coerceAtLeast(0.0)
        val x = plugin.config.getDouble("settings.crafter.particle-location.x", 0.5)
        val y = plugin.config.getDouble("settings.crafter.particle-location.y", 1.0)
        val z = plugin.config.getDouble("settings.crafter.particle-location.z", 0.5)
        val deltaX = plugin.config.getDouble("settings.crafter.particle-delta.x", 0.2).coerceIn(0.0, 10.0)
        val deltaY = plugin.config.getDouble("settings.crafter.particle-delta.y", 0.0).coerceIn(0.0, 10.0)
        val deltaZ = plugin.config.getDouble("settings.crafter.particle-delta.z", 0.2).coerceIn(0.0, 10.0)
        val speed = plugin.config.getDouble("settings.crafter.particle-speed", 0.08).coerceIn(0.0, 10.0)
        val particle = configuredParticle()
        val iterator = blocks.iterator()
        while (iterator.hasNext()) {
            val position = iterator.next()
            val world = Bukkit.getWorld(position.worldName) ?: continue
            if (!world.isChunkLoaded(position.x shr 4, position.z shr 4)) continue
            val baseLocation = Location(world, position.x + x, position.y + y, position.z + z)
            val location = Location(world, position.x + 0.5, position.y + 0.5, position.z + 0.5)
            if (world.getNearbyPlayers(location, range).isEmpty()) continue
            repeat(particleCount) {
                val particleLocation = baseLocation.clone().add(
                    randomOffset(deltaX),
                    randomOffset(deltaY),
                    randomOffset(deltaZ)
                )
                world.spawnParticle(particle, particleLocation, 0, 0.0, 1.0, 0.0, speed)
            }
        }
    }

    private fun randomOffset(delta: Double): Double =
        if (delta <= 0.0) 0.0 else Random.nextDouble(-delta, delta)

    private fun configuredParticle(): Particle {
        val name = plugin.config.getString(
            "settings.crafter.particle",
            "TRIAL_SPAWNER_DETECTION_OMINOUS"
        )!!.uppercase(Locale.ROOT)
        if (name == cachedParticleName) return cachedParticle

        cachedParticleName = name
        cachedParticle = try {
            Particle.valueOf(name)
        } catch (exception: IllegalArgumentException) {
            plugin.logger.warning("Invalid Crafter particle '$name'; using TRIAL_SPAWNER_DETECTION_OMINOUS.")
            Particle.TRIAL_SPAWNER_DETECTION_OMINOUS
        }
        return cachedParticle
    }

    private fun save() {
        if (!plugin.dataFolder.exists() && !plugin.dataFolder.mkdirs()) {
            throw IOException("Could not create plugin data directory.")
        }
        val yaml = YamlConfiguration()
        yaml.set("locations", blocks.map { position ->
            mapOf(
                "world" to position.worldName,
                "x" to position.x,
                "y" to position.y,
                "z" to position.z
            )
        })
        yaml.save(dataFile)
    }

    private data class BlockPosition(val worldName: String, val x: Int, val y: Int, val z: Int)
}
