package dev.kinetic.app

import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExternalCatalogGuardTest {
    @Test fun only_recovered_idle_state_allows_catalog_or_context_operations() {
        assertTrue(externalCatalogIdle(true, false, false, false, false, false))
        assertFalse(externalCatalogIdle(false, false, false, false, false, false))
        for (busy in 0..4) {
            assertFalse(externalCatalogIdle(true, busy == 0, busy == 1, busy == 2, busy == 3, busy == 4))
        }
    }
}
