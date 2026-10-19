package com.blacksingularities.openexplorer

import com.blacksingularities.openexplorer.update.Updater
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
}
