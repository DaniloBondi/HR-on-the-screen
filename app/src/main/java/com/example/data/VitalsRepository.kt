package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow

class VitalsRepository(private val dao: VitalsDao) {

    val allSessions: Flow<List<HeartRateSession>> = dao.getAllSessions()
    val allTokens: Flow<List<ApiToken>> = dao.getAllTokens()

    fun getDashboardPreferences(): Flow<List<DashboardPreference>> = flow {
        dao.getDashboardPreferences().collect { prefs ->
            if (prefs.isEmpty()) {
                val defaultPrefs = listOf(
                    DashboardPreference("vitals_dial", isVisible = true, orderIndex = 0, alertThresholdBpm = 145),
                    DashboardPreference("beats_chart", isVisible = true, orderIndex = 1),
                    DashboardPreference("zone_distribution", isVisible = true, orderIndex = 2),
                    DashboardPreference("advanced_metrics", isVisible = true, orderIndex = 3),
                    DashboardPreference("streamlit_status", isVisible = true, orderIndex = 4)
                )
                dao.saveDashboardPreferences(defaultPrefs)
                emit(defaultPrefs)
            } else {
                emit(prefs)
            }
        }
    }

    suspend fun savePreferences(prefs: List<DashboardPreference>) {
        dao.saveDashboardPreferences(prefs)
    }

    suspend fun insertSession(session: HeartRateSession) {
        dao.insertSession(session)
    }

    suspend fun deleteSession(id: Int) {
        dao.deleteSession(id)
    }

    suspend fun insertToken(token: ApiToken) {
        dao.insertToken(token)
    }

    suspend fun deleteToken(token: String) {
        dao.deleteToken(token)
    }
}
