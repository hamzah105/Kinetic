package dev.kinetic.data.model

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.*

@RunWith(AndroidJUnit4::class)
class AndroidLocalCapabilityProbeTest {
    @Test fun reports_ordinary_facts_without_touching_provider_preferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        check(context.packageName != "dev.kinetic.app")
        val preferences = context.getSharedPreferences("kinetic_provider_settings", Context.MODE_PRIVATE)
        val before = preferences.all.toMap()
        val facts = AndroidLocalCapabilityProbe(context).read()
        assertEquals(Build.VERSION.SDK_INT, facts.apiLevel)
        assertEquals(Build.SUPPORTED_ABIS.toSet(), facts.supportedAbis)
        assertTrue(facts.physicalRamBytes == null || facts.physicalRamBytes!! > 0)
        assertTrue(facts.availableStorageBytes == null || facts.availableStorageBytes!! >= 0)
        assertEquals(before, preferences.all)
    }
}
