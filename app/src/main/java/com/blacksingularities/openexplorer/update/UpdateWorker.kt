package com.blacksingularities.openexplorer.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.blacksingularities.openexplorer.OpenExplorerApp
import java.util.concurrent.TimeUnit

/**
 * Sprawdzanie aktualizacji w tle (co kilka godzin, także gdy aplikacja jest zamknięta):
 * pobranie nowej wersji i cicha instalacja (Android 12+), a gdy to niemożliwe – powiadomienie.
 */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as OpenExplorerApp
        val s = app.settings
        if (!s.autoUpdate) return Result.success()
        val updater = app.updater
        val state = updater.check(quiet = true)
        val info = when (state) {
            is UpdateState.Available -> state.info
            is UpdateState.Ready -> state.info
            else -> return Result.success()
        }
        if (!s.updateAutoDownload || (s.updateWifiOnly && !updater.unmetered())) {
            if (state is UpdateState.Available) {
                updater.notify(
                    "Dostępna wersja ${info.version}",
                    "Dotknij, aby zaktualizować OpenExplorer",
                    android.content.Intent(applicationContext, com.blacksingularities.openexplorer.MainActivity::class.java)
                        .setAction(Updater.ACTION_INSTALL).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return Result.success()
        }
        updater.download(info) ?: return Result.retry()
        val ready = updater.state as? UpdateState.Ready ?: return Result.success()
        // Gdy aplikacja jest otwarta – baner; w tle: cicha instalacja albo powiadomienie.
        if (!Updater.foreground) {
            if (s.updateAutoInstall && updater.canInstallSilently()) updater.install(ready)
            else updater.notifyReady(ready.info)
        }
        return Result.success()
    }

    companion object {
        private const val NAME = "update-check"

        fun schedule(context: Context, replace: Boolean = false) {
            val s = (context.applicationContext as OpenExplorerApp).settings
            val wm = WorkManager.getInstance(context)
            if (!s.autoUpdate) {
                wm.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS, 1, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(if (s.updateWifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                        .setRequiresStorageNotLow(true)
                        .build(),
                )
                .setInitialDelay(30, TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(
                NAME,
                if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
