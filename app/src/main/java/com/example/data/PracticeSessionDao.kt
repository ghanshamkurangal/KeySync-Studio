package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PracticeSessionDao {
    @Query("SELECT * FROM practice_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<PracticeSession>>

    @Query("SELECT * FROM practice_sessions WHERE username = :username ORDER BY timestamp DESC")
    fun getSessionsForUser(username: String): Flow<List<PracticeSession>>

    @Query("SELECT * FROM practice_sessions WHERE username IS NULL ORDER BY timestamp DESC")
    fun getGuestSessions(): Flow<List<PracticeSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: PracticeSession)

    @Delete
    suspend fun deleteSession(session: PracticeSession)

    @Query("DELETE FROM practice_sessions")
    suspend fun clearAll()
}
