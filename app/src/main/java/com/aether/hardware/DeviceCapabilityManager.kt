package com.aether.hardware

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.system.Os
import android.system.OsConstants
import java.io.File

/**
 * Universal Hardware Capability Tier calculated from measured runtime metrics,
 * independent of device brand or marketing names.
 */
enum class DeviceHardwareTier(val label: String, val description: String) {
    BASIC("Basic Tier", "Limited RAM (<6GB) or basic cores. Cloud inference recommended."),
    STANDARD("Standard Tier", "Moderate RAM (6-8GB), 8 cores. Supports small GGUF models (<2B)."),
    PERFORMANCE("Performance Tier", "Good RAM (8-12GB), ARM64-v8a. Supports quantized 3B-4B models."),
    HIGH_PERFORMANCE("High Performance Tier", "Substantial RAM (12-16GB), NEON/DotProd. Supports 7B models."),
    ULTRA_PERFORMANCE("Ultra Performance Tier", "Elite RAM (>16GB), all high-perf cores. Maximum local context.")
}

enum class ThermalStatus(val level: Int, val label: String) {
    NORMAL(0, "Cool / Normal"),
    LIGHT(1, "Light Warmth"),
    MODERATE(2, "Moderate Throttling"),
    SEVERE(3, "Severe Throttling"),
    CRITICAL(4, "Critical Overheat"),
    EMERGENCY(5, "Emergency Throttling")
}

enum class PowerPreference {
    AUTOMATIC,
    MAX_PERFORMANCE,
    BALANCED,
    BATTERY_SAVER
}

data class HardwareCapabilityProfile(
    val manufacturer: String,
    val model: String,
    val androidSdk: Int,
    val cpuArchitecture: String,
    val supportedAbis: List<String>,
    val cpuCoreCount: Int,
    val totalRamMb: Long,
    val availableRamMb: Long,
    val isLowMemory: Boolean,
    val storageAvailableMb: Long,
    val batteryPercent: Int,
    val isCharging: Boolean,
    val thermalStatus: ThermalStatus,
    val supportsNeon: Boolean,
    val supportsDotProduct: Boolean,
    val supportsFp16: Boolean,
    val calculatedTier: DeviceHardwareTier,
    val userOverrideTier: DeviceHardwareTier? = null,
    val maxSafeModelRamMb: Long,
    val recommendedThreads: Int
) {
    val effectiveTier: DeviceHardwareTier
        get() = userOverrideTier ?: calculatedTier
}

/**
 * Universal Device Capability Assessor.
 * Inspects real Android platform APIs at runtime without vendor lock-in.
 */
class DeviceCapabilityManager(private val context: Context) {

    private var userOverrideTier: DeviceHardwareTier? = null
    private var powerPreference: PowerPreference = PowerPreference.AUTOMATIC

    fun setUserOverrideTier(tier: DeviceHardwareTier?) {
        this.userOverrideTier = tier
    }

    fun setPowerPreference(preference: PowerPreference) {
        this.powerPreference = preference
    }

    fun getPowerPreference(): PowerPreference = powerPreference

    fun assessCapabilities(): HardwareCapabilityProfile {
        val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager?.getMemoryInfo(memInfo)

        val totalRamMb = memInfo.totalMem / (1024 * 1024)
        val availRamMb = memInfo.availMem / (1024 * 1024)
        val isLowMem = memInfo.lowMemory

        // Cores & Architecture
        val coreCount = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val abis = Build.SUPPORTED_ABIS.toList()
        val arch = System.getProperty("os.arch") ?: "arm64"

        // Parse CPU flags from /proc/cpuinfo if available
        val (hasNeon, hasDotProd, hasFp16) = parseCpuFeatures()

        // Storage availability
        val storageAvailMb = try {
            val stat = context.filesDir.freeSpace / (1024 * 1024)
            stat
        } catch (_: Exception) {
            4096L
        }

        // Battery State
        val batteryFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, batteryFilter)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 80
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: 100
        val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else 80
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL

        // Thermal status
        val thermalStatus = getRealThermalStatus()

        // Dynamic Hardware Tier determination
        val calculatedTier = calculateTier(
            totalRamMb = totalRamMb,
            availRamMb = availRamMb,
            cores = coreCount,
            thermal = thermalStatus,
            hasDotProd = hasDotProd
        )

        // Safe RAM calculation
        val osReserveMb = 2048L
        val rawBudget = (availRamMb - osReserveMb).coerceAtLeast(0L).coerceAtMost((totalRamMb * 0.55).toLong())
        val thermalThrottleMultiplier = when (thermalStatus) {
            ThermalStatus.NORMAL, ThermalStatus.LIGHT -> 1.0
            ThermalStatus.MODERATE -> 0.75
            ThermalStatus.SEVERE -> 0.50
            ThermalStatus.CRITICAL, ThermalStatus.EMERGENCY -> 0.0
        }
        val safeModelRamMb = (rawBudget * thermalThrottleMultiplier).toLong()

        // Thread count recommendation
        val recommendedThreads = calculateThreadCount(coreCount, thermalStatus, powerPreference)

        return HardwareCapabilityProfile(
            manufacturer = Build.MANUFACTURER ?: "Unknown",
            model = Build.MODEL ?: "Generic Android",
            androidSdk = Build.VERSION.SDK_INT,
            cpuArchitecture = arch,
            supportedAbis = abis,
            cpuCoreCount = coreCount,
            totalRamMb = totalRamMb,
            availableRamMb = availRamMb,
            isLowMemory = isLowMem,
            storageAvailableMb = storageAvailMb,
            batteryPercent = batteryPct,
            isCharging = isCharging,
            thermalStatus = thermalStatus,
            supportsNeon = hasNeon,
            supportsDotProduct = hasDotProd,
            supportsFp16 = hasFp16,
            calculatedTier = calculatedTier,
            userOverrideTier = userOverrideTier,
            maxSafeModelRamMb = safeModelRamMb,
            recommendedThreads = recommendedThreads
        )
    }

    private fun getRealThermalStatus(): ThermalStatus {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val code = powerManager?.currentThermalStatus ?: 0
            return when (code) {
                PowerManager.THERMAL_STATUS_NONE -> ThermalStatus.NORMAL
                PowerManager.THERMAL_STATUS_LIGHT -> ThermalStatus.LIGHT
                PowerManager.THERMAL_STATUS_MODERATE -> ThermalStatus.MODERATE
                PowerManager.THERMAL_STATUS_SEVERE -> ThermalStatus.SEVERE
                PowerManager.THERMAL_STATUS_CRITICAL -> ThermalStatus.CRITICAL
                PowerManager.THERMAL_STATUS_EMERGENCY,
                PowerManager.THERMAL_STATUS_SHUTDOWN -> ThermalStatus.EMERGENCY
                else -> ThermalStatus.NORMAL
            }
        }
        return ThermalStatus.NORMAL
    }

    private fun parseCpuFeatures(): Triple<Boolean, Boolean, Boolean> {
        var neon = true
        var dotProd = false
        var fp16 = true

        try {
            val file = File("/proc/cpuinfo")
            if (file.exists() && file.canRead()) {
                val content = file.readText().lowercase()
                neon = content.contains("neon") || content.contains("asimd")
                dotProd = content.contains("asimddp") || content.contains("dotprod")
                fp16 = content.contains("fphp") || content.contains("asimdhp") || content.contains("fp16")
            }
        } catch (_: Exception) {
            // Standard arm64 has NEON and FP16 by default
            neon = true
            fp16 = true
        }

        return Triple(neon, dotProd, fp16)
    }

    private fun calculateTier(
        totalRamMb: Long,
        availRamMb: Long,
        cores: Int,
        thermal: ThermalStatus,
        hasDotProd: Boolean
    ): DeviceHardwareTier {
        if (thermal >= ThermalStatus.CRITICAL) {
            return DeviceHardwareTier.BASIC
        }
        return when {
            totalRamMb >= 15000 && cores >= 8 -> DeviceHardwareTier.ULTRA_PERFORMANCE
            totalRamMb >= 11000 && cores >= 8 -> DeviceHardwareTier.HIGH_PERFORMANCE
            totalRamMb >= 7500 && cores >= 6 -> DeviceHardwareTier.PERFORMANCE
            totalRamMb >= 5500 -> DeviceHardwareTier.STANDARD
            else -> DeviceHardwareTier.BASIC
        }
    }

    private fun calculateThreadCount(cores: Int, thermal: ThermalStatus, preference: PowerPreference): Int {
        if (thermal >= ThermalStatus.SEVERE) return 2
        return when (preference) {
            PowerPreference.MAX_PERFORMANCE -> (cores - 1).coerceIn(2, 8)
            PowerPreference.BALANCED, PowerPreference.AUTOMATIC -> (cores / 2).coerceIn(2, 6)
            PowerPreference.BATTERY_SAVER -> 2
        }
    }
}
