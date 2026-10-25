package com.blacksingularities.openexplorer

import com.blacksingularities.openexplorer.update.Updater
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdaterTest {
    @Test
    fun versions() {
        assertTrue(Updater.isNewer("1.0.1", "1.0.0"))
        assertTrue(Updater.isNewer("1.0.0.12", "1.0.0"))
        assertTrue(Updater.isNewer("1.0.0.12", "1.0.0.9"))
        assertTrue(Updater.isNewer("1.0.1", "1.0-dev"))
        assertFalse(Updater.isNewer("1.0.0", "1.0.0"))
        assertFalse(Updater.isNewer("0.9.9", "1.0.0"))
        assertFalse(Updater.isNewer("", "1.0.0"))
    }

    @Test
    fun digest() {
        val hex = "A".repeat(64)
        assertEquals("a".repeat(64), Updater.parseDigest("sha256:$hex"))
        assertEquals("", Updater.parseDigest("sha512:$hex"))
        assertEquals("", Updater.parseDigest("sha256:xyz"))
        assertEquals("", Updater.parseDigest(""))
        assertEquals("", Updater.parseDigest(null))
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Updater.sha256Hex("abc".toByteArray()),
        )
    }

    @Test
    fun signers() {
        assertTrue(Updater.sameSigner(setOf("a"), setOf("old", "a")))
        assertFalse(Updater.sameSigner(setOf("b"), setOf("a")))
        assertFalse(Updater.sameSigner(emptySet(), setOf("a")))
        assertFalse(Updater.sameSigner(setOf("a"), emptySet()))
    }
}
