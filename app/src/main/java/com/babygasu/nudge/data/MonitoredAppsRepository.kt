package com.babygasu.nudge.data

import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentHashMap

class MonitoredAppsRepository(private val dao: MonitoredAppDao) {
    private val enabledCache = ConcurrentHashMap<String, Boolean>()

    fun observe(): Flow<List<MonitoredApp>> = dao.observeAll()

    /** Remembers that an app posts notifications so it can be listed in the Privacy Centre (off by default). */
    suspend fun recordSeen(packageName: String, label: String) {
        if (enabledCache.containsKey(packageName)) return
        val existing = dao.get(packageName)
        if (existing == null) dao.insertIfAbsent(MonitoredApp(packageName, label, false, System.currentTimeMillis()))
        enabledCache[packageName] = existing?.enabled ?: false
    }

    suspend fun isEnabled(packageName: String): Boolean =
        enabledCache[packageName] ?: (dao.get(packageName)?.enabled ?: false).also { enabledCache[packageName] = it }

    suspend fun setEnabled(packageName: String, label: String, enabled: Boolean) {
        val existing = dao.get(packageName)
        dao.upsert(MonitoredApp(packageName, label, enabled, existing?.firstSeenAt ?: System.currentTimeMillis()))
        enabledCache[packageName] = enabled
    }

    suspend fun listIfAbsent(packageName: String, label: String) {
        dao.insertIfAbsent(MonitoredApp(packageName, label, false, System.currentTimeMillis()))
    }

    fun clearCache() = enabledCache.clear()
}
