package com.example.data

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import com.example.data.PracticeSession
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object FirebaseService {
    private const val TAG = "FirebaseService"
    private var isInitialized = false
    private var isFirebaseConfiguredSuccessfully = false

    private val _isFirebaseAvailable = MutableStateFlow(false)
    val isFirebaseAvailable: StateFlow<Boolean> = _isFirebaseAvailable.asStateFlow()

    private val _currentFirebaseUser = MutableStateFlow<FirebaseUser?>(null)
    val currentFirebaseUser: StateFlow<FirebaseUser?> = _currentFirebaseUser.asStateFlow()

    private val _forceLocalMode = MutableStateFlow(false)
    val forceLocalMode: StateFlow<Boolean> = _forceLocalMode.asStateFlow()

    fun toggleFirebaseMode() {
        _forceLocalMode.value = !_forceLocalMode.value
        updateAvailability()
    }

    fun isFirebaseConfigured(): Boolean {
        return isFirebaseConfiguredSuccessfully
    }

    private fun updateAvailability() {
        _isFirebaseAvailable.value = isFirebaseConfiguredSuccessfully && !_forceLocalMode.value
    }

    fun init(context: Context) {
        if (isInitialized) return

        val apiKey = BuildConfig.FIREBASE_API_KEY
        val projectId = BuildConfig.FIREBASE_PROJECT_ID
        val appId = BuildConfig.FIREBASE_APP_ID

        val isConfigured = apiKey.isNotEmpty() && apiKey != "your_firebase_api_key" &&
                projectId.isNotEmpty() && projectId != "your_firebase_project_id" &&
                appId.isNotEmpty() && appId != "your_firebase_app_id"

        if (!isConfigured) {
            Log.w(TAG, "Firebase credentials not provided or are defaults. Operating in sandboxed Room mode.")
            isFirebaseConfiguredSuccessfully = false
            _isFirebaseAvailable.value = false
            isInitialized = true
            return
        }

        try {
            val options = FirebaseOptions.Builder()
                .setApiKey(apiKey)
                .setProjectId(projectId)
                .setApplicationId(appId)
                .build()

            FirebaseApp.initializeApp(context, options)
            isInitialized = true
            isFirebaseConfiguredSuccessfully = true
            updateAvailability()
            Log.d(TAG, "Firebase successfully initialized programmatically.")

            // Monitor auth changes
            FirebaseAuth.getInstance().addAuthStateListener { auth ->
                _currentFirebaseUser.value = auth.currentUser
                Log.d(TAG, "Firebase Auth state changed: ${auth.currentUser?.email}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing programmatically: ${e.message}", e)
            isFirebaseConfiguredSuccessfully = false
            _isFirebaseAvailable.value = false
        }
    }

    // Helper to wait for Task completion safely using standard suspendCancellableCoroutine
    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitSafe(): T {
        if (isComplete) {
            val e = exception
            if (e != null) throw e
            return result
        }
        return suspendCancellableCoroutine { cont ->
            addOnCompleteListener { task ->
                val e = task.exception
                if (e != null) {
                    cont.resumeWithException(e)
                } else {
                    cont.resume(task.result)
                }
            }
        }
    }

    suspend fun signUpWithEmail(email: String, p: String, dName: String): FirebaseUser? {
        if (!_isFirebaseAvailable.value) throw IllegalStateException("Firebase is not initialized")
        val auth = FirebaseAuth.getInstance()
        val result = auth.createUserWithEmailAndPassword(email, p).awaitSafe()
        val user = result.user
        if (user != null) {
            // Update profile with display name
            val profileUpdates = com.google.firebase.auth.userProfileChangeRequest {
                displayName = dName
            }
            user.updateProfile(profileUpdates).awaitSafe()
        }
        return user
    }

    suspend fun signInWithEmail(email: String, p: String): FirebaseUser? {
        if (!_isFirebaseAvailable.value) throw IllegalStateException("Firebase is not initialized")
        val auth = FirebaseAuth.getInstance()
        val result = auth.signInWithEmailAndPassword(email, p).awaitSafe()
        return result.user
    }

    fun signOut() {
        if (_isFirebaseAvailable.value) {
            FirebaseAuth.getInstance().signOut()
        }
    }

    suspend fun saveCustomSongToFirestore(song: Song) {
        if (!_isFirebaseAvailable.value) return
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()

        val notesList = song.notes.map { note ->
            mapOf(
                "pitch" to note.pitch,
                "lyric" to note.lyric,
                "frequency" to note.frequency
            )
        }

        val data = mapOf(
            "userId" to user.uid,
            "id" to song.id,
            "title" to song.title,
            "artist" to song.artist,
            "difficulty" to song.difficulty,
            "description" to song.description,
            "notes" to notesList,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("users")
            .document(user.uid)
            .collection("custom_songs")
            .document(song.id)
            .set(data)
            .awaitSafe()
        Log.d(TAG, "Custom song saved to Firestore: ${song.title}")
    }

    suspend fun deleteCustomSongFromFirestore(songId: String) {
        if (!_isFirebaseAvailable.value) return
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()

        db.collection("users")
            .document(user.uid)
            .collection("custom_songs")
            .document(songId)
            .delete()
            .awaitSafe()
        Log.d(TAG, "Custom song deleted from Firestore: $songId")
    }

    suspend fun fetchCustomSongsFromFirestore(): List<Song> {
        if (!_isFirebaseAvailable.value) return emptyList()
        val user = FirebaseAuth.getInstance().currentUser ?: return emptyList()
        val db = FirebaseFirestore.getInstance()

        val querySnapshot = db.collection("users")
            .document(user.uid)
            .collection("custom_songs")
            .get()
            .awaitSafe()

        return querySnapshot.documents.mapNotNull { doc ->
            val map = doc.data ?: return@mapNotNull null
            val id = map["id"] as? String ?: return@mapNotNull null
            val title = map["title"] as? String ?: ""
            val artist = map["artist"] as? String ?: "Traditional"
            val difficulty = map["difficulty"] as? String ?: "Easy"
            val description = map["description"] as? String ?: ""
            @Suppress("UNCHECKED_CAST")
            val rawNotes = map["notes"] as? List<Map<String, Any?>> ?: emptyList()

            val notes = rawNotes.map { noteMap ->
                SongNote(
                    pitch = noteMap["pitch"] as? String ?: "C4",
                    lyric = noteMap["lyric"] as? String ?: "",
                    frequency = (noteMap["frequency"] as? Number)?.toFloat() ?: 261.63f
                )
            }

            Song(
                id = id,
                title = title,
                artist = artist,
                difficulty = difficulty,
                description = description,
                notes = notes
            )
        }
    }

    suspend fun saveFavoritesToFirestore(favoriteIds: List<String>) {
        if (!_isFirebaseAvailable.value) return
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()

        val data = mapOf(
            "favoriteIds" to favoriteIds,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("users")
            .document(user.uid)
            .collection("favorites_metadata")
            .document("list")
            .set(data)
            .awaitSafe()
        Log.d(TAG, "Favorites updated in Firestore: $favoriteIds")
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun fetchFavoritesFromFirestore(): List<String> {
        if (!_isFirebaseAvailable.value) return emptyList()
        val user = FirebaseAuth.getInstance().currentUser ?: return emptyList()
        val db = FirebaseFirestore.getInstance()

        val doc = db.collection("users")
            .document(user.uid)
            .collection("favorites_metadata")
            .document("list")
            .get()
            .awaitSafe()

        if (doc.exists()) {
            val list = doc.get("favoriteIds") as? List<String>
            if (list != null) return list
        }
        return emptyList()
    }

    suspend fun saveSessionToFirestore(session: PracticeSession) {
        if (!_isFirebaseAvailable.value) return
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val db = FirebaseFirestore.getInstance()

        val data = session.toMap(user.uid)
        db.collection("users")
            .document(user.uid)
            .collection("sessions")
            .add(data)
            .awaitSafe()
        Log.d(TAG, "Session saved to Firestore: ${session.songTitle}")
    }

    suspend fun fetchSessionsFromFirestore(): List<PracticeSession> {
        if (!_isFirebaseAvailable.value) return emptyList()
        val user = FirebaseAuth.getInstance().currentUser ?: return emptyList()
        val db = FirebaseFirestore.getInstance()

        val querySnapshot = db.collection("users")
            .document(user.uid)
            .collection("sessions")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .get()
            .awaitSafe()

        var idCounter = 1
        return querySnapshot.documents.mapNotNull { doc ->
            val map = doc.data ?: return@mapNotNull null
            map.toPracticeSession(idCounter++)
        }
    }

    // Convert entity properties to firestore compatibility
    private fun PracticeSession.toMap(userId: String): Map<String, Any?> {
        return mapOf(
            "userId" to userId,
            "songTitle" to songTitle,
            "accuracy" to accuracy,
            "timestamp" to timestamp,
            "correctNotes" to correctNotes,
            "totalNotes" to totalNotes,
            "isCompleted" to isCompleted,
            "audioFilePath" to audioFilePath
        )
    }

    private fun Map<String, Any?>.toPracticeSession(docId: Int): PracticeSession {
        return PracticeSession(
            id = docId,
            songTitle = this["songTitle"] as? String ?: "",
            accuracy = (this["accuracy"] as? Number)?.toFloat() ?: 0f,
            timestamp = (this["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            correctNotes = (this["correctNotes"] as? Number)?.toInt() ?: 0,
            totalNotes = (this["totalNotes"] as? Number)?.toInt() ?: 0,
            isCompleted = this["isCompleted"] as? Boolean ?: false,
            audioFilePath = this["audioFilePath"] as? String,
            username = null
        )
    }
}
