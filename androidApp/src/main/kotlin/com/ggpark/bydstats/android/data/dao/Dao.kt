package com.ggpark.bydstats.android.data.dao

import androidx.room.*
import com.ggpark.bydstats.android.data.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DataPointDao {
    @Insert
    suspend fun insert(point: DataPointEntity)

    @Query("SELECT * FROM data_points WHERE timestamp >= :from AND timestamp <= :to ORDER BY timestamp ASC")
    suspend fun queryRange(from: Long, to: Long): List<DataPointEntity>

    @Query("SELECT * FROM data_points ORDER BY timestamp DESC LIMIT 1")
    suspend fun latest(): DataPointEntity?

    @Query("DELETE FROM data_points WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("SELECT * FROM data_points ORDER BY timestamp ASC")
    fun allFlow(): Flow<List<DataPointEntity>>
}

@Dao
interface ChargingSessionDao {
    @Insert
    suspend fun insert(session: ChargingSessionEntity): Long

    @Update
    suspend fun update(session: ChargingSessionEntity)

    @Delete
    suspend fun delete(session: ChargingSessionEntity)

    @Query("SELECT * FROM charging_sessions ORDER BY startTime DESC")
    fun allFlow(): Flow<List<ChargingSessionEntity>>

    @Query("SELECT * FROM charging_sessions WHERE endTime IS NULL")
    suspend fun incomplete(): List<ChargingSessionEntity>

    @Query("SELECT * FROM charging_sessions WHERE startTime >= :from AND startTime <= :to ORDER BY startTime DESC")
    suspend fun queryRange(from: Long, to: Long): List<ChargingSessionEntity>
}

@Dao
interface DrivingSessionDao {
    @Insert
    suspend fun insert(session: DrivingSessionEntity): Long

    @Update
    suspend fun update(session: DrivingSessionEntity)

    @Delete
    suspend fun delete(session: DrivingSessionEntity)

    @Query("SELECT * FROM driving_sessions ORDER BY startTime DESC")
    fun allFlow(): Flow<List<DrivingSessionEntity>>

    @Query("SELECT * FROM driving_sessions WHERE endTime IS NULL")
    suspend fun incomplete(): List<DrivingSessionEntity>

    @Query("SELECT * FROM driving_sessions WHERE startTime >= :from AND startTime <= :to ORDER BY startTime DESC")
    suspend fun queryRange(from: Long, to: Long): List<DrivingSessionEntity>
}
