package com.blacksingularities.openexplorer

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.blacksingularities.openexplorer.browser.AdBlocker
import com.blacksingularities.openexplorer.data.Settings
import com.blacksingularities.openexplorer.data.Store

class OpenExplorerApp : Application(), ImageLoaderFactory {
    lateinit var settings: Settings
        private set
    lateinit var store: Store
        private set
    lateinit var adBlocker: AdBlocker
        private set

    /** Jedna instancja aktualizatora dla interfejsu, zadania w tle i odbiornika instalacji. */
    val updater: com.blacksingularities.openexplorer.update.Updater by lazy {
        com.blacksingularities.openexplorer.update.Updater(this)
    }

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        store = Store(this)
        adBlocker = AdBlocker(this)
        com.blacksingularities.openexplorer.update.UpdateWorker.schedule(this)
    }

    /** Logo kafelków trzymane w pamięci podręcznej na dysku – strona startowa działa bez sieci. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("icons")).maxSizeBytes(32L * 1024 * 1024).build() }
        .respectCacheHeaders(false)
        .crossfade(true)
        .build()
}
