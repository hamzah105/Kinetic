package dev.kinetic.app.ui

import org.junit.Test
import kotlin.test.*
import androidx.compose.ui.graphics.luminance

class StellarPresentationTest {
    @Test fun short_ime_viewports_allow_vertical_overflow_without_changing_normal_portrait() {
        assertTrue(stellarNeedsVerticalOverflow(124f, 1f))
        assertTrue(stellarNeedsVerticalOverflow(359f, 1f))
        assertFalse(stellarNeedsVerticalOverflow(360f, 1f))
        assertFalse(stellarNeedsVerticalOverflow(800f, 1f))
        assertTrue(stellarNeedsVerticalOverflow(500f, 2f))
        assertFalse(stellarNeedsVerticalOverflow(800f, 2f))
    }
    @Test fun explicit_theme_overrides_system_for_palette_and_system_bar_contrast() {
        assertTrue(usesDarkPalette(StellarTheme.DARK, systemDark = false))
        assertTrue(usesDarkPalette(StellarTheme.DARK, systemDark = true))
        assertFalse(usesDarkPalette(StellarTheme.LIGHT, systemDark = true))
        assertFalse(usesDarkPalette(StellarTheme.LIGHT, systemDark = false))
        assertTrue(usesDarkPalette(StellarTheme.SYSTEM, systemDark = true))
        assertFalse(usesDarkPalette(StellarTheme.SYSTEM, systemDark = false))
    }
    @Test fun layouts_support_compact_medium_expanded_and_large_fonts() {
        assertEquals(StellarLayout.COMPACT, stellarLayout(393f, 1f))
        assertEquals(StellarLayout.MEDIUM, stellarLayout(700f, 1f))
        assertEquals(StellarLayout.EXPANDED, stellarLayout(1000f, 1f))
        assertEquals(StellarLayout.COMPACT, stellarLayout(1000f, 2f))
    }
    @Test fun theme_choices_are_explicit_and_include_system() {
        assertEquals(listOf("Follow system", "Kinetic Light", "Kinetic Stellar Dark"), StellarTheme.entries.map { it.label })
    }
    @Test fun light_and_stellar_dark_text_roles_meet_normal_text_contrast() {
        listOf(false, true).forEach { dark ->
            val colors = stellarColors(dark)
            listOf(colors.onSurface to colors.surface, colors.onBackground to colors.background,
                colors.onPrimary to colors.primary, colors.onSurfaceVariant to colors.surfaceVariant).forEach { (ink, paper) ->
                val ratio = (maxOf(ink.luminance(), paper.luminance()) + 0.05f) /
                    (minOf(ink.luminance(), paper.luminance()) + 0.05f)
                assertTrue(ratio >= 4.5f, "Contrast was $ratio in dark=$dark")
            }
        }
    }
    @Test fun action_disclosures_remain_truthful_and_describe_external_data() {
        assertTrue(actionDisclosure("compose_email").contains("review, send or discard"))
        assertTrue(actionDisclosure("open_dialer").contains("No call is placed"))
        assertTrue(actionDisclosure("share_text").contains("Kinetic sends nothing"))
        assertTrue(actionDisclosure("copy_text_to_clipboard").contains("no automatic undo"))
        assertTrue(actionDisclosure("open_settings").contains("No setting is changed"))
        assertTrue(actionDisclosure("open_https_url").contains("network request"))
    }
}
