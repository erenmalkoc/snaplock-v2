package com.erenium.snaplock.autofill

import org.junit.Assert.assertEquals
import org.junit.Test

class AutofillSetupTest {

    @Test
    fun android14NeedsTheCredentialProviderToo() {
        assertEquals(AutofillSetup.PASSKEYS_OFF, AutofillStatus.setupOf(autofill = true, credentialManager = true, provider = false))
        assertEquals(AutofillSetup.ENABLED, AutofillStatus.setupOf(autofill = true, credentialManager = true, provider = true))
    }

    @Test
    fun olderAndroidOnlyNeedsAutofill() {
        assertEquals(AutofillSetup.ENABLED, AutofillStatus.setupOf(autofill = true, credentialManager = false, provider = false))
        assertEquals(AutofillSetup.DISABLED, AutofillStatus.setupOf(autofill = false, credentialManager = false, provider = false))
    }

    @Test
    fun withoutAutofillNothingElseMatters() {
        assertEquals(AutofillSetup.DISABLED, AutofillStatus.setupOf(autofill = false, credentialManager = true, provider = true))
    }
}
