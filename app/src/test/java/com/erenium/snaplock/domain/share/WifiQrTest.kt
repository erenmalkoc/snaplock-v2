package com.erenium.snaplock.domain.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WifiQrTest {
    @Test
    fun buildsTheUsualWpaText() {
        assertEquals("WIFI:T:WPA;S:Home;P:secret;;", WifiQr.payload("Home", "secret"))
    }

    @Test
    fun otherSecurityTypesUseTheirOwnCode() {
        assertEquals("WIFI:T:WEP;S:Old;P:12345;;", WifiQr.payload("Old", "12345", WifiSecurity.Wep))
        assertEquals("WIFI:T:WPA;S:New;P:pw;;", WifiQr.payload("New", "pw", WifiSecurity.Wpa))
    }

    @Test
    fun anOpenNetworkCarriesNoPassword() {
        val text = WifiQr.payload("Cafe", "must-not-leak", WifiSecurity.Open)

        assertEquals("WIFI:T:nopass;S:Cafe;;", text)
        assertFalse(text.contains("must-not-leak"))
    }

    @Test
    fun aHiddenNetworkIsMarked() {
        assertEquals("WIFI:T:WPA;S:Home;P:secret;H:true;;", WifiQr.payload("Home", "secret", hidden = true))
        assertEquals("WIFI:T:nopass;S:Home;H:true;;", WifiQr.payload("Home", "", WifiSecurity.Open, hidden = true))
    }

    @Test
    fun reservedCharactersAreEscapedInTheNameAndThePassword() {
        val text = WifiQr.payload("a;b,c:d\\e\"f", "p;w,:\\\"x")

        assertEquals("WIFI:T:WPA;S:a\\;b\\,c\\:d\\\\e\\\"f;P:p\\;w\\,\\:\\\\\\\"x;;", text)
    }

    @Test
    fun aSemicolonInThePasswordCannotInjectAnotherField() {
        val text = WifiQr.payload("Home", "x;H:true")

        assertEquals("WIFI:T:WPA;S:Home;P:x\\;H\\:true;;", text)
        assertFalse(text.contains(";H:true"))
    }

    @Test
    fun numericPasswordsStayLiteral() {
        assertEquals("WIFI:T:WPA;S:Home;P:12345678;;", WifiQr.payload("Home", "12345678"))
        assertEquals("WIFI:T:WPA;S:DEADBEEF;P:cafe0123;;", WifiQr.payload("DEADBEEF", "cafe0123"))
    }

    @Test
    fun turkishLettersAndSpacesPassThrough() {
        assertEquals("WIFI:T:WPA;S:Şeker Ağı;P:ğüşiöç İI;;", WifiQr.payload("Şeker Ağı", "ğüşiöç İI"))
    }
}
