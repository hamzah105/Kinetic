package dev.kinetic.data.model

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import dev.kinetic.core.model.LocalDeviceFacts

/** Ordinary read-only facts. No package scans, hardware IDs, downloads or telemetry. */
class AndroidLocalCapabilityProbe(context: Context) {
    private val application = context.applicationContext

    fun read(): LocalDeviceFacts {
        val manager = application.getSystemService(ActivityManager::class.java)
        val ram = runCatching {
            ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }.totalMem
        }.getOrNull()?.takeIf { it > 0 }
        return LocalDeviceFacts(
            apiLevel = Build.VERSION.SDK_INT,
            supportedAbis = Build.SUPPORTED_ABIS.toSet(),
            physicalRamBytes = ram,
            memoryClassMiB = runCatching { manager.memoryClass }.getOrNull()?.takeIf { it > 0 },
            availableStorageBytes = runCatching { StatFs(application.filesDir.path).availableBytes }.getOrNull(),
            emulatorLikely = Build.HARDWARE in setOf("ranchu", "goldfish") || Build.MODEL.startsWith("sdk_gphone"),
        )
    }
}
