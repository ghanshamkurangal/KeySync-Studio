package com.example.data

import kotlinx.coroutines.flow.Flow

class PracticeRepository(
    private val practiceSessionDao: PracticeSessionDao,
    private val userDao: UserDao
) {
    val allSessions: Flow<List<PracticeSession>> = practiceSessionDao.getAllSessions()

    fun getSessionsForUser(username: String): Flow<List<PracticeSession>> {
        return practiceSessionDao.getSessionsForUser(username)
    }

    fun getGuestSessions(): Flow<List<PracticeSession>> {
        return practiceSessionDao.getGuestSessions()
    }

    suspend fun insertSession(session: PracticeSession) {
        practiceSessionDao.insertSession(session)
    }

    suspend fun deleteSession(session: PracticeSession) {
        practiceSessionDao.deleteSession(session)
    }

    suspend fun clearAll() {
        practiceSessionDao.clearAll()
    }

    // User authentication queries and insertion
    suspend fun registerUser(user: User): Boolean {
        return try {
            // Check if user already exists
            val existing = userDao.getUserByUsername(user.username)
            if (existing != null) return false
            
            userDao.insertUser(user)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getUserByUsername(username: String): User? {
        return userDao.getUserByUsername(username)
    }

    suspend fun getUserByEmail(email: String): User? {
        return userDao.getUserByEmail(email)
    }
}
