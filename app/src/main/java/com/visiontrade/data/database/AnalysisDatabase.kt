package com.visiontrade.data.database

import android.content.Context
import androidx.room.*
import com.visiontrade.domain.models.AnalysisHistoryEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// ============= ROOM ENTITY =============
@Entity(tableName = "analysis_history")
data class AnalysisHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val symbol: String,
    val timeframe: String,
    val signal: String,
    val confidence: String,
    val trend: String,
    val reason: String,
    val entryZone: String,
    val support: String,
    val resistance: String,
    val imageData: ByteArray? = null,
    val timestampString: String = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AnalysisHistoryEntity
        return id == other.id && timestampString == other.timestampString
    }

    override fun hashCode(): Int = id.hashCode() * 31 + timestampString.hashCode()
}

// ============= DAO =============
@Dao
interface AnalysisHistoryDao {
    
    @Insert
    suspend fun insert(entity: AnalysisHistoryEntity): Long
    
    @Update
    suspend fun update(entity: AnalysisHistoryEntity)
    
    @Delete
    suspend fun delete(entity: AnalysisHistoryEntity)
    
    @Query("SELECT * FROM analysis_history ORDER BY timestampString DESC LIMIT 1")
    suspend fun getLatest(): AnalysisHistoryEntity?
    
    @Query("SELECT * FROM analysis_history ORDER BY timestampString DESC")
    fun getAll(): Flow<List<AnalysisHistoryEntity>>
    
    @Query("SELECT * FROM analysis_history WHERE symbol = :symbol ORDER BY timestampString DESC")
    fun getBySymbol(symbol: String): Flow<List<AnalysisHistoryEntity>>
    
    @Query("SELECT * FROM analysis_history WHERE signal = :signal ORDER BY timestampString DESC")
    fun getBySignal(signal: String): Flow<List<AnalysisHistoryEntity>>
    
    @Query("DELETE FROM analysis_history")
    suspend fun deleteAll()
    
    @Query("SELECT COUNT(*) FROM analysis_history")
    suspend fun getCount(): Int
}

// ============= DATABASE =============
@Database(
    entities = [AnalysisHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AnalysisDatabase : RoomDatabase() {
    abstract fun analysisHistoryDao(): AnalysisHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AnalysisDatabase? = null

        fun getDatabase(context: Context): AnalysisDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AnalysisDatabase::class.java,
                    "visiontrade_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// ============= CONVERTERS =============
object AnalysisConverters {
    
    fun entityToDomain(entity: AnalysisHistoryEntity): AnalysisHistoryEntry {
        return AnalysisHistoryEntry(
            id = entity.id,
            symbol = entity.symbol,
            timeframe = entity.timeframe,
            signal = entity.signal,
            confidence = entity.confidence,
            trend = entity.trend,
            reason = entity.reason,
            entryZone = entity.entryZone,
            support = entity.support,
            resistance = entity.resistance,
            imageData = entity.imageData,
            timestamp = try {
                LocalDateTime.parse(entity.timestampString, DateTimeFormatter.ISO_DATE_TIME)
            } catch (e: Exception) {
                LocalDateTime.now()
            }
        )
    }
    
    fun domainToEntity(domain: AnalysisHistoryEntry): AnalysisHistoryEntity {
        return AnalysisHistoryEntity(
            id = domain.id,
            symbol = domain.symbol,
            timeframe = domain.timeframe,
            signal = domain.signal,
            confidence = domain.confidence,
            trend = domain.trend,
            reason = domain.reason,
            entryZone = domain.entryZone,
            support = domain.support,
            resistance = domain.resistance,
            imageData = domain.imageData,
            timestampString = domain.timestamp.format(DateTimeFormatter.ISO_DATE_TIME)
        )
    }
}
