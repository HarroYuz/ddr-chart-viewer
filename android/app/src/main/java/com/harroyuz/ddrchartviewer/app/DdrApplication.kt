package com.harroyuz.ddrchartviewer.app

import android.app.Application
import android.content.Context
import androidx.work.*
import com.harroyuz.ddrchartviewer.data.Repository
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class DdrApplication : Application() {
    val repository by lazy { Repository(this) }
    override fun onCreate() {
        super.onCreate()
        // CookieManager/WebSettings are initialized on main, also for a worker-only process start.
        repository
        val request = PeriodicWorkRequestBuilder<CatalogWorker>(24, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("daily-ddr-catalog", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}

class CatalogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        (applicationContext as DdrApplication).repository.refreshCatalog()
        Result.success()
    } catch (e: CancellationException) { throw e }
      catch (_: Exception) { Result.retry() }
}
