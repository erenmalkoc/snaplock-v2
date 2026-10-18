package com.erenium.snaplock.domain.importer

import com.erenium.snaplock.domain.otp.OtpParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CsvImporterTest {

    @Test
    fun bitwardenWithQuotesMultilineNotesAndTotp() {
        val csv = "folder,favorite,type,name,notes,fields,reprompt,login_uri,login_username,login_password,login_totp\r\n" +
            "Social,1,login,Twitter,,,0,https://twitter.com,me@example.com,\"pa,ss\"\"word\",\r\n" +
            ",,login,GitHub,\"two\r\nlines\",,0,https://github.com/login,octocat,gh-pass,JBSWY3DPEHPK3PXP\r\n"

        val import = CsvImporter.parse(csv)!!

        assertEquals("Bitwarden", import.source)
        val (twitter, github) = import.entries
        assertEquals("pa,ss\"word", twitter.password)
        assertEquals("Social", twitter.folder)
        assertEquals("me@example.com", twitter.username)
        assertEquals("two\r\nlines", github.notes)
        assertNull(github.folder)
        val imported = OtpParser.parse(github.otpUri!!)!!
        val original = OtpParser.parse("JBSWY3DPEHPK3PXP")!!
        assertEquals(original.secret.toList(), imported.secret.toList())
        assertEquals(original.period to original.digits, imported.period to imported.digits)
    }

    @Test
    fun chromeExport() {
        val csv = "name,url,username,password,note\nexample.com,https://example.com/,alice,s3cret,\n"

        val import = CsvImporter.parse(csv)!!

        assertEquals("Chrome", import.source)
        assertEquals(ImportedEntry("example.com", "alice", "s3cret", "https://example.com/", null, null, null), import.entries.single())
    }

    @Test
    fun firefoxHasNoTitleSoTheHostIsUsed() {
        val csv = "\"url\",\"username\",\"password\",\"httpRealm\",\"formActionOrigin\",\"guid\",\"timeCreated\",\"timeLastUsed\",\"timePasswordChanged\"\n" +
            "\"https://www.mozilla.org\",\"fox\",\"pw\",,\"https://www.mozilla.org\",\"{x}\",\"1\",\"1\",\"1\"\n"

        val import = CsvImporter.parse(csv)!!

        assertEquals("Firefox", import.source)
        assertEquals("mozilla.org", import.entries.single().title)
    }

    @Test
    fun protonPassFallsBackToEmailAndKeepsVaults() {
        val csv = "type,name,url,email,username,password,note,totp,createTime,modifyTime,vault\n" +
            "login,Proton,https://proton.me,me@proton.me,,pw,,otpauth://totp/Proton?secret=JBSWY3DPEHPK3PXP,1,1,Personal\n"

        val entry = CsvImporter.parse(csv)!!.entries.single()

        assertEquals("me@proton.me", entry.username)
        assertEquals("Personal", entry.folder)
        assertEquals("Proton Pass", CsvImporter.parse(csv)!!.source)
    }

    @Test
    fun keePassXcAndOnePasswordHeaders() {
        val keepassxc = "\"Group\",\"Title\",\"Username\",\"Password\",\"URL\",\"Notes\",\"TOTP\",\"Icon\",\"Last Modified\",\"Created\"\n" +
            "\"Root/Web\",\"Forum\",\"me\",\"pw\",\"forum.example\",\"\",\"\",\"0\",\"x\",\"x\"\n"
        val onePassword = "Title,Url,Username,Password,OTPAuth,Favorite,Archived,Tags,Notes\nBank,https://bank.example,client,pw,,false,false,,\n"

        assertEquals("KeePassXC", CsvImporter.parse(keepassxc)!!.source)
        assertEquals("Root/Web", CsvImporter.parse(keepassxc)!!.entries.single().folder)
        assertEquals("1Password", CsvImporter.parse(onePassword)!!.source)
        assertEquals("client", CsvImporter.parse(onePassword)!!.entries.single().username)
    }

    @Test
    fun passwordsKeepTheirSpacesAndBomIsIgnored() {
        val csv = "﻿name,url,username,password\nSpace,https://s.example,u,\"  padded  \"\n"

        assertEquals("  padded  ", CsvImporter.parse(csv)!!.entries.single().password)
    }

    @Test
    fun filesWithoutAPasswordColumnAreRejected() {
        assertNull(CsvImporter.parse("name,phone\nAlice,555\n"))
        assertNull(CsvImporter.parse("name,url,username,password\n"))
    }
}
