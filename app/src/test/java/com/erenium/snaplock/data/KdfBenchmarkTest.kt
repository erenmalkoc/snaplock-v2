package com.erenium.snaplock.data

import com.erenium.snaplock.data.crypto.KdfBenchmark
import com.erenium.snaplock.domain.model.EncryptionSettings
import com.erenium.snaplock.domain.model.KdfAlgorithm
import com.erenium.snaplock.domain.model.VaultCipher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class KdfBenchmarkTest {

    private val benchmark = KdfBenchmark()

    @Test
    fun moreTimeAllowsMoreArgon2Passes() = runBlocking {
        val settings = EncryptionSettings(VaultCipher.AES_256, KdfAlgorithm.ARGON2ID, 1, 1024L * 1024, 1)

        // The first run warms the JIT up, which would skew the comparison.
        benchmark.iterationsFor(settings, targetMillis = 200)
        val quick = benchmark.iterationsFor(settings, targetMillis = 200)
        val slow = benchmark.iterationsFor(settings, targetMillis = 2_000)

        assertTrue("$quick passes", quick >= 1)
        // Ten times the time; timing on a busy test machine varies, so only ask for twice the passes.
        assertTrue("$quick vs $slow", slow >= quick * 2)
    }

    @Test
    fun aesKdfGetsRoundsInTheThousands() = runBlocking {
        val settings = EncryptionSettings(VaultCipher.AES_256, KdfAlgorithm.AES_KDF, 1, 0, 0)

        val rounds = benchmark.iterationsFor(settings, targetMillis = 200)

        assertTrue("$rounds rounds", rounds >= 1_000)
    }
}
