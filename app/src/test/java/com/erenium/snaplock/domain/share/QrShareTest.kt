package com.erenium.snaplock.domain.share

import com.erenium.snaplock.domain.model.CustomField
import com.erenium.snaplock.domain.model.EntryDetail
import com.erenium.snaplock.domain.otp.OtpParameters
import com.erenium.snaplock.domain.otp.OtpParser
import java.util.UUID
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QrShareTest {
    private fun entry(
        title: String = "Bank",
        username: String? = "client",
        password: String? = "pw",
        url: String? = null,
        fields: List<CustomField> = emptyList(),
        otp: OtpParameters? = null
    ) = EntryDetail(UUID.randomUUID(), title, username, password, url, null, null, customFields = fields, otp = otp)

    private val plain = WifiSettings(ssid = "ignored")

    @Test
    fun offersWhateverTheEntryActuallyHolds() {
        val full = entry(
            url = "example.com",
            fields = listOf(CustomField("PIN", "4711", true), CustomField("Empty", "", false)),
            otp = OtpParameters("12345678901234567890".toByteArray())
        )

        val kinds = QrShare.options(full).map { it.kind to it.fieldName }

        assertEquals(
            listOf(
                QrShareKind.Password to null,
                QrShareKind.Wifi to null,
                QrShareKind.Username to null,
                QrShareKind.Url to null,
                QrShareKind.OneTimeCode to null,
                QrShareKind.Field to "PIN"
            ),
            kinds
        )
    }

    @Test
    fun skipsBlankPartsAndOffersNoWifiWithoutAPassword() {
        val sparse = entry(username = "  ", password = "", url = "")

        assertTrue(QrShare.options(sparse).isEmpty())
        assertEquals(listOf(QrShareKind.Username), QrShare.options(entry(username = "me", password = null)).map { it.kind })
    }

    @Test
    fun theDefaultIsThePasswordUnlessTheTitleIsAboutAWifiNetwork() {
        assertEquals(QrShareKind.Password, QrShare.defaultOption(entry(title = "Bank"))?.kind)
        listOf("Home Wi-Fi", "WiFi Ofis", "wi fi", "Kablosuz Ağ", "WLAN 5GHz", "Router SSID", "EV WİFİ").forEach {
            assertEquals(it, QrShareKind.Wifi, QrShare.defaultOption(entry(title = it))?.kind)
        }
    }

    @Test
    fun aWifiTitleWithoutAPasswordFallsBackToWhatIsThere() {
        val open = entry(title = "Cafe Wi-Fi", username = "CafeNet", password = null)

        assertEquals(QrShareKind.Username, QrShare.defaultOption(open)?.kind)
        assertNull(QrShare.defaultOption(entry(username = null, password = null)))
    }

    @Test
    fun theNetworkNameStartsAsTheUserNameThenTheTitle() {
        assertEquals("TP-Link_8F2A", QrShare.defaultSsid(entry(title = "Home", username = "TP-Link_8F2A")))
        assertEquals("Home", QrShare.defaultSsid(entry(title = "Home", username = " ")))
        assertEquals("Home", QrShare.defaultSsid(entry(title = "Home", username = null)))
    }

    @Test
    fun theTextForEachChoiceIsTheStoredValue() {
        val e = entry(
            username = "me@example.com",
            password = "p;w",
            fields = listOf(CustomField("PIN", "4711", true), CustomField("Backup", "abc def", false))
        )

        assertEquals("p;w", QrShare.payload(e, QrShareOption(QrShareKind.Password), plain))
        assertEquals("me@example.com", QrShare.payload(e, QrShareOption(QrShareKind.Username), plain))
        assertEquals("4711", QrShare.payload(e, QrShareOption(QrShareKind.Field, "PIN"), plain))
        assertEquals("abc def", QrShare.payload(e, QrShareOption(QrShareKind.Field, "Backup"), plain))
        assertNull(QrShare.payload(e, QrShareOption(QrShareKind.Field, "Missing"), plain))
    }

    @Test
    fun aWebAddressGetsASchemeSoPhonesOpenIt() {
        fun url(value: String) = QrShare.payload(entry(url = value), QrShareOption(QrShareKind.Url), plain)

        assertEquals("https://example.com/login", url("example.com/login"))
        assertEquals("https://example.com", url("  example.com  "))
        assertEquals("http://intranet.local/a", url("http://intranet.local/a"))
        assertEquals("ssh://host", url("ssh://host"))
    }

    @Test
    fun theWifiTextUsesThePasswordAndTheChosenSettings() {
        val e = entry(password = "s3;cret")

        assertEquals(
            "WIFI:T:WPA;S:Home;P:s3\\;cret;;",
            QrShare.payload(e, QrShareOption(QrShareKind.Wifi), WifiSettings("Home"))
        )
        assertEquals(
            "WIFI:T:WEP;S:Old;P:s3\\;cret;H:true;;",
            QrShare.payload(e, QrShareOption(QrShareKind.Wifi), WifiSettings("Old", WifiSecurity.Wep, hidden = true))
        )
        assertEquals(
            "WIFI:T:nopass;S:Cafe;;",
            QrShare.payload(e, QrShareOption(QrShareKind.Wifi), WifiSettings("Cafe", WifiSecurity.Open))
        )
    }

    @Test
    fun aWifiCodeNeedsANetworkName() {
        assertNull(QrShare.payload(entry(), QrShareOption(QrShareKind.Wifi), WifiSettings("   ")))
        assertNull(QrShare.payload(entry(), QrShareOption(QrShareKind.Wifi), WifiSettings("")))
    }

    @Test
    fun theNetworkNameKeepsItsSpaces() {
        val text = QrShare.payload(entry(), QrShareOption(QrShareKind.Wifi), WifiSettings(" My Net "))

        assertEquals("WIFI:T:WPA;S: My Net ;P:pw;;", text)
    }

    @Test
    fun theOneTimeCodeIsShownAsASetupLinkAnotherAppCanRead() {
        val secret = "12345678901234567890".toByteArray()
        val totp = entry(otp = OtpParameters(secret, issuer = "GitHub", account = "me"))
        val hotp = entry(otp = OtpParameters(secret, counter = 7))

        val totpText = QrShare.payload(totp, QrShareOption(QrShareKind.OneTimeCode), plain)!!
        val hotpText = QrShare.payload(hotp, QrShareOption(QrShareKind.OneTimeCode), plain)!!

        assertTrue(totpText.startsWith("otpauth://totp/"))
        assertTrue(hotpText.startsWith("otpauth://hotp/"))
        assertArrayEquals(secret, OtpParser.parse(totpText)!!.secret)
        assertEquals(7L, OtpParser.parse(hotpText)!!.counter)
    }

    @Test
    fun nothingIsShownForAValueThatIsNotThere() {
        val bare = entry(username = null, password = null)

        QrShareKind.entries.forEach { kind ->
            assertNull(kind.name, QrShare.payload(bare, QrShareOption(kind, "x"), plain))
        }
    }

    @Test
    fun everyChoiceHasItsOwnKey() {
        val e = entry(
            url = "a.b",
            fields = listOf(CustomField("A", "1", false), CustomField("B", "2", false)),
            otp = OtpParameters("12345678901234567890".toByteArray())
        )

        val keys = QrShare.options(e).map { it.key }

        assertEquals(keys.size, keys.toSet().size)
        assertTrue("Field:A" in keys && "Field:B" in keys)
        assertNotNull(QrShare.options(e).firstOrNull { it.key == "Wifi" })
    }
}
