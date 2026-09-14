package dev.kinetic.data.androidcapabilities

import android.content.Intent
import android.net.MailTo
import android.net.Uri
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.kinetic.core.tools.SettingsDestination
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidIntentFactoryTest {
    @Test
    fun HTTPSIntentUsesOnlyActionViewAndValidatedUri() {
        val intent = AndroidIntentFactory.create(
            AndroidCapabilityRequest.OpenHttpsUrl("https://example.com/path?q=1"),
        )

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("https://example.com/path?q=1", intent.dataString)
        assertNull(intent.component)
        assertNull(intent.`package`)
        assertEquals(0, intent.flags)
    }

    @Test
    fun unsafeUrlCannotBecomeAnIntent() {
        assertThrows(IllegalArgumentException::class.java) {
            AndroidIntentFactory.create(AndroidCapabilityRequest.OpenHttpsUrl("intent://unsafe"))
        }
    }

    @Test
    fun shareUsesSystemChooserAroundUntargetedPlainTextSendIntent() {
        val chooser = AndroidIntentFactory.create(
            AndroidCapabilityRequest.ShareText("KINETIC_SHARE_OK"),
        )
        val send = chooser.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)

        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertNull(chooser.component)
        assertNull(chooser.`package`)
        assertEquals(Intent.ACTION_SEND, send?.action)
        assertEquals("text/plain", send?.type)
        assertEquals("KINETIC_SHARE_OK", send?.getStringExtra(Intent.EXTRA_TEXT))
        assertNull(send?.component)
        assertNull(send?.`package`)
    }

    @Test
    fun settingsActionsAreInternallyAllowlisted() {
        assertEquals(
            Settings.ACTION_SETTINGS,
            AndroidIntentFactory.create(
                AndroidCapabilityRequest.OpenSettings(SettingsDestination.GENERAL),
            ).action,
        )
        assertEquals(
            Settings.ACTION_WIFI_SETTINGS,
            AndroidIntentFactory.create(
                AndroidCapabilityRequest.OpenSettings(SettingsDestination.WIFI),
            ).action,
        )
    }

    @Test
    fun dialerUsesOnlyActionDialAndInternallyConstructedTelUri() {
        val intent = AndroidIntentFactory.create(
            AndroidCapabilityRequest.OpenDialer("+92 300-1234567"),
        )

        assertEquals(Intent.ACTION_DIAL, intent.action)
        assertEquals("tel", intent.data?.scheme)
        assertEquals("+923001234567", intent.data?.schemeSpecificPart)
        assertNull(intent.component)
        assertNull(intent.`package`)
        assertNull(intent.type)
        assertEquals(0, intent.flags)
        assertThrows(IllegalArgumentException::class.java) {
            AndroidIntentFactory.create(AndroidCapabilityRequest.OpenDialer("tel:+923001234567"))
        }
    }

    @Test
    fun emailRecipientOnlyRoundTripsThroughAndroidMailTo() {
        val intent = AndroidIntentFactory.create(
            AndroidCapabilityRequest.ComposeEmail(
                recipient = "test+tag@example.com",
                subject = null,
                body = null,
            ),
        )
        val parsed = MailTo.parse(intent.dataString)

        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("mailto", intent.data?.scheme)
        assertEquals("test+tag@example.com", parsed.to)
        assertNull(parsed.subject)
        assertNull(parsed.body)
        assertArrayEquals(
            arrayOf("test+tag@example.com"),
            intent.getStringArrayExtra(Intent.EXTRA_EMAIL),
        )
    }

    @Test
    fun emailOptionalSubjectAndBodyCombinationsRoundTripThroughAndroidMailTo() {
        listOf(
            AndroidCapabilityRequest.ComposeEmail("test@example.com", "Subject with spaces", null),
            AndroidCapabilityRequest.ComposeEmail("test@example.com", null, "Body with spaces"),
            AndroidCapabilityRequest.ComposeEmail(
                "test@example.com",
                "KINETIC_EMAIL_OK",
                "Hello from Kinetic",
            ),
        ).forEach { request ->
            val parsed = MailTo.parse(AndroidIntentFactory.create(request).dataString)
            assertEquals(request.recipient, parsed.to)
            assertEquals(request.subject, parsed.subject)
            assertEquals(request.body, parsed.body)
        }
    }

    @Test
    fun emailUriEncodingPreservesReservedUnicodePercentPlusAndMultilineData() {
        val subject = "Spaces + & = ? # 100% café 🚀"
        val body = "First + & = ? # 50%\nمرحبا 🌍"
        val intent = AndroidIntentFactory.create(
            AndroidCapabilityRequest.ComposeEmail("test@example.com", subject, body),
        )
        val parsed = intent.decodeMailtoOnce()

        assertEquals("mailto", intent.data?.scheme)
        assertEquals("test@example.com", parsed.recipient)
        assertEquals(subject, parsed.fields["subject"])
        assertEquals("First + & = ? # 50%\r\nمرحبا 🌍", parsed.fields["body"])
        assertEquals(subject, intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals(body, intent.getStringExtra(Intent.EXTRA_TEXT))
        assertNull(intent.data?.fragment)
    }

    @Test
    fun percentLookingHeaderInjectionRemainsLiteralDataAfterOneDecode() {
        val subject = "Safe%0d%0aBcc=attacker@example.com"
        val intent = AndroidIntentFactory.create(
            AndroidCapabilityRequest.ComposeEmail("test@example.com", subject, "Safe body"),
        )
        val parsed = intent.decodeMailtoOnce()

        assertEquals(subject, parsed.fields["subject"])
        assertFalse(parsed.fields.keys.any { it.equals("bcc", ignoreCase = true) })
        assertTrue(intent.dataString.orEmpty().contains("%250d%250a"))
    }

    @Test
    fun actualRecipientOrSubjectHeaderInjectionIsRejected() {
        listOf(
            AndroidCapabilityRequest.ComposeEmail("test@example.com\r\nBcc:x@example.com", null, null),
            AndroidCapabilityRequest.ComposeEmail("test@example.com", "Hello\rBcc: x@example.com", null),
            AndroidCapabilityRequest.ComposeEmail("test@example.com", "Hello\nBcc: x@example.com", null),
        ).forEach { request ->
            assertThrows(IllegalArgumentException::class.java) {
                AndroidIntentFactory.create(request)
            }
        }
    }

    @Test
    fun emailUsesUntargetedSendToMailtoWithoutAttachmentsOrTransmission() {
        val intent = AndroidIntentFactory.create(
            AndroidCapabilityRequest.ComposeEmail(
                recipient = "test@example.com",
                subject = "KINETIC_EMAIL_OK",
                body = "Hello from Kinetic",
            ),
        )

        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("mailto", intent.data?.scheme)
        assertEquals("test@example.com", MailTo.parse(intent.dataString).to)
        assertEquals("KINETIC_EMAIL_OK", MailTo.parse(intent.dataString).subject)
        assertEquals("Hello from Kinetic", MailTo.parse(intent.dataString).body)
        assertEquals("KINETIC_EMAIL_OK", intent.getStringExtra(Intent.EXTRA_SUBJECT))
        assertEquals("Hello from Kinetic", intent.getStringExtra(Intent.EXTRA_TEXT))
        assertFalse(intent.hasExtra(Intent.EXTRA_STREAM))
        assertNull(intent.component)
        assertNull(intent.`package`)
        assertNull(intent.type)
        assertEquals(0, intent.flags)
    }

    /** Decode the encoded RFC 6068 wire representation exactly once. */
    private fun Intent.decodeMailtoOnce(): DecodedMailto {
        val encoded = requireNotNull(data?.encodedSchemeSpecificPart)
        val encodedRecipient = encoded.substringBefore('?')
        val encodedQuery = encoded.substringAfter('?', missingDelimiterValue = "")
        val fields = encodedQuery
            .takeIf(String::isNotEmpty)
            ?.split('&')
            ?.associate { field ->
                Uri.decode(field.substringBefore('=')) to
                    Uri.decode(field.substringAfter('=', missingDelimiterValue = ""))
            }
            .orEmpty()
        return DecodedMailto(Uri.decode(encodedRecipient), fields)
    }

    private data class DecodedMailto(
        val recipient: String,
        val fields: Map<String, String>,
    )
}
