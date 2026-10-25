package com.blacksingularities.openexplorer.browser

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
 * Odświeżanie list filtrów włączonych w ustawieniach (StevenBlack, EasyList, EasyPrivacy, Polskie Filtry)
 * raz w tygodniu – tylko gdy któraś jest pobrana i blokowanie jest włączone. Wbudowana lista nie wymaga odświeżania.
 */
class FiltersWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as OpenExplorerApp
        val s = app.settings
        if (!s.adBlock || !app.adBlocker.hasLists) return Result.success()
        if (System.currentTimeMillis() - s.filtersUpdated < MIN_AGE) return Result.success()
        return runCatching { app.adBlocker.update() }
            .fold(
                onSuccess = {
                    s.filtersUpdated = System.currentTimeMillis()
                    Result.success()
                },
                onFailure = { if (runAttemptCount < 3) Result.retry() else Result.success() },
            )
    }

    companion object {
        private const val NAME = "filters-update"
        private val MIN_AGE = TimeUnit.DAYS.toMillis(6)

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<FiltersWorker>(7, TimeUnit.DAYS, 1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true)
                        .setRequiresStorageNotLow(true)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
