package com.visiontrade.data.repository

import com.visiontrade.data.database.AnalysisConverters
import com.visiontrade.data.database.AnalysisDatabase
import com.visiontrade.domain.models.AnalysisHistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for analysis history
 * Handles all data persistence operations
 */
class AnalysisRepository(private val database: AnalysisDatabase) {

    private val dao = database.analysisHistoryDao()

    suspend fun save(entry: AnalysisHistoryEntry): Long {
        val entity = AnalysisConverters.domainToEntity(entry)
        return dao.insert(entity)
    }

    suspend fun delete(entry: AnalysisHistoryEntry) {
        val entity = AnalysisConverters.domainToEntity(entry)
        dao.delete(entity)
    }

    fun getAll(): Flow<List<AnalysisHistoryEntry>> {
        return dao.getAll().map { entities ->
            entities.map { AnalysisConverters.entityToDomain(it) }
        }
    }

    fun getBySymbol(symbol: String): Flow<List<AnalysisHistoryEntry>> {
        return dao.getBySymbol(symbol).map { entities ->
            entities.map { AnalysisConverters.entityToDomain(it) }
        }
    }

    fun getBySignal(signal: String): Flow<List<AnalysisHistoryEntry>> {
        return dao.getBySignal(signal).map { entities ->
            entities.map { AnalysisConverters.entityToDomain(it) }
        }
    }

    suspend fun deleteAll() {
        dao.deleteAll()
    }

    suspend fun getCount(): Int {
        return dao.getCount()
    }

    suspend fun getLatest(): AnalysisHistoryEntry? {
        val entity = dao.getLatest() ?: return null
        return AnalysisConverters.entityToDomain(entity)
    }
}
