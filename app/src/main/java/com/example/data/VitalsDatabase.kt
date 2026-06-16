package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "hr_sessions")
data class HeartRateSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val startTime: Long,
    val endTime: Long,
    val averageHeartRate: Int,
    val maxHeartRate: Int,
    val caloriesBurned: Int,
    val durationSeconds: Long,
    val bpmSequenceJson: String, // Comma-separated BPM points for simplicity in chart rebuilding
    val label: String
)

@Entity(tableName = "dashboard_preferences")
data class DashboardPreference(
    @PrimaryKey val widgetId: String,
    val isVisible: Boolean,
    val orderIndex: Int,
    val alertThresholdBpm: Int = 140
)

@Entity(tableName = "api_tokens")
data class ApiToken(
    @PrimaryKey val token: String,
    val label: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
)

@Dao
interface VitalsDao {
    @Query("SELECT * FROM hr_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<HeartRateSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: HeartRateSession)

    @Query("DELETE FROM hr_sessions WHERE id = :id")
    suspend fun deleteSession(id: Int)

    @Query("SELECT * FROM dashboard_preferences ORDER BY orderIndex ASC")
    fun getDashboardPreferences(): Flow<List<DashboardPreference>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDashboardPreferences(prefs: List<DashboardPreference>)

    @Query("SELECT * FROM api_tokens ORDER BY createdAt DESC")
    fun getAllTokens(): Flow<List<ApiToken>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertToken(token: ApiToken)

    @Query("DELETE FROM api_tokens WHERE token = :token")
    suspend fun deleteToken(token: String)
}

@Database(entities = [HeartRateSession::class, DashboardPreference::class, ApiToken::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vitalsDao(): VitalsDao
}
