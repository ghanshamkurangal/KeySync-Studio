package com.example.ui

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioAnalyser
import com.example.data.*
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi

class KeySyncViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = PracticeRepository(database.practiceSessionDao(), database.userDao())

    private val _activeUser = MutableStateFlow<User?>(null)
    val activeUser = _activeUser.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val sessionHistory: StateFlow<List<PracticeSession>> = _activeUser
        .flatMapLatest { user ->
            if (user != null) {
                repository.getSessionsForUser(user.username)
            } else {
                repository.getGuestSessions()
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val isFirebaseAvailable = FirebaseService.isFirebaseAvailable
    val firebaseUser = FirebaseService.currentFirebaseUser
    val forceLocalMode = FirebaseService.forceLocalMode

    fun toggleFirebaseMode() {
        FirebaseService.toggleFirebaseMode()
    }

    private val _firestoreSessions = MutableStateFlow<List<PracticeSession>>(emptyList())
    val firestoreSessions = _firestoreSessions.asStateFlow()

    private val _customSongs = MutableStateFlow<List<Song>>(emptyList())
    val customSongs = _customSongs.asStateFlow()

    private val _aiAnalyzing = MutableStateFlow(false)
    val aiAnalyzing = _aiAnalyzing.asStateFlow()

    private val _favoriteSongIds = MutableStateFlow<List<String>>(emptyList())
    val favoriteSongIds = _favoriteSongIds.asStateFlow()

    private val sharedPrefs = application.getSharedPreferences("keysync_prefs", android.content.Context.MODE_PRIVATE)
    private val _customApiKey = MutableStateFlow(sharedPrefs.getString("custom_api_key", "") ?: "")
    val customApiKey = _customApiKey.asStateFlow()

    fun saveCustomApiKey(key: String) {
        sharedPrefs.edit().putString("custom_api_key", key).apply()
        _customApiKey.value = key
    }

    init {
        FirebaseService.init(application)
        com.example.midi.MidiService.init(application)
        
        // Support pre-registering default test accounts on database initialization (as signup is removed)
        viewModelScope.launch {
            try {
                listOf(
                    User("pianist", "Pro Pianist", "pianist@keysync.com", "password"),
                    User("cyclist", "Active Cyclist", "cyclist@keysync.com", "cyclist")
                ).forEach { defaultUser ->
                    val existing = repository.getUserByUsername(defaultUser.username)
                    if (existing == null) {
                        repository.registerUser(defaultUser)
                    }
                }
                // Auto-login default local user 'pianist' immediately so they go directly to the home page!
                val defaultActive = repository.getUserByUsername("pianist")
                if (defaultActive != null) {
                    _activeUser.value = defaultActive
                }
            } catch (e: Exception) {
                android.util.Log.e("KeySyncViewModel", "Failed to populate default users", e)
            }
        }

        // Listen to high-precision hardware MIDI key events to trigger practice evaluation instantly!
        viewModelScope.launch {
            com.example.midi.MidiService.midiNoteOnEvents.collect { note: String ->
                if (_isPlayingSongMode.value) {
                    evaluatePlayedNotes(listOf(note))
                }
            }
        }

        // Monitor Firebase Auth user and fetch sessions, custom songs, favorites, and auto-sync offline progress
        viewModelScope.launch {
            FirebaseService.currentFirebaseUser.collect { user ->
                if (user != null) {
                    refreshFirestoreSessions()
                    refreshFirestoreCustomSongs()
                    refreshFirestoreFavorites()
                    syncLocalHistoryToCloud()
                } else {
                    _firestoreSessions.value = emptyList()
                    _customSongs.value = emptyList()
                    _favoriteSongIds.value = emptyList()
                }
            }
        }
    }

    fun refreshFirestoreSessions() {
        viewModelScope.launch {
            try {
                if (FirebaseService.isFirebaseAvailable.value && FirebaseService.currentFirebaseUser.value != null) {
                    val sessions = FirebaseService.fetchSessionsFromFirestore()
                    _firestoreSessions.value = sessions
                }
            } catch (e: Exception) {
                android.util.Log.e("KeySyncViewModel", "Error fetching Firestore sessions: ${e.message}", e)
            }
        }
    }

    fun refreshFirestoreCustomSongs() {
        viewModelScope.launch {
            try {
                if (FirebaseService.isFirebaseAvailable.value && FirebaseService.currentFirebaseUser.value != null) {
                    val songs = FirebaseService.fetchCustomSongsFromFirestore()
                    _customSongs.value = songs
                }
            } catch (e: Exception) {
                android.util.Log.e("KeySyncViewModel", "Error fetching Firestore custom songs: ${e.message}", e)
            }
        }
    }

    fun refreshFirestoreFavorites() {
        viewModelScope.launch {
            try {
                if (FirebaseService.isFirebaseAvailable.value && FirebaseService.currentFirebaseUser.value != null) {
                    val favs = FirebaseService.fetchFavoritesFromFirestore()
                    _favoriteSongIds.value = favs
                }
            } catch (e: Exception) {
                android.util.Log.e("KeySyncViewModel", "Error fetching Firestore favorites: ${e.message}", e)
            }
        }
    }

    private fun syncLocalHistoryToCloud() {
        viewModelScope.launch {
            try {
                if (FirebaseService.isFirebaseAvailable.value && FirebaseService.currentFirebaseUser.value != null) {
                    // Collect first emission of offline or local guest sessions
                    val guests = repository.getGuestSessions().stateIn(this).value
                    if (guests.isNotEmpty()) {
                        guests.forEach { session ->
                            FirebaseService.saveSessionToFirestore(session)
                        }
                    }
                    
                    val activeLocalUser = _activeUser.value
                    if (activeLocalUser != null) {
                        val userSessions = repository.getSessionsForUser(activeLocalUser.username).stateIn(this).value
                        userSessions.forEach { session ->
                            FirebaseService.saveSessionToFirestore(session)
                        }
                    }
                    refreshFirestoreSessions()
                }
            } catch (e: Exception) {
                android.util.Log.e("KeySyncViewModel", "Error syncing local history to cloud: ${e.message}", e)
            }
        }
    }

    fun addCustomSong(
        title: String,
        artist: String,
        difficulty: String,
        description: String,
        notesInput: String
    ) {
        viewModelScope.launch {
            try {
                val pitchList = notesInput.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }

                val songNotes = pitchList.map { pitch ->
                    SongNote(
                        pitch = pitch,
                        lyric = pitch,
                        frequency = getFrequencyForPitch(pitch)
                    )
                }

                if (songNotes.isEmpty()) return@launch

                val customSong = Song(
                    id = "custom_" + System.currentTimeMillis(),
                    title = title,
                    artist = artist.ifEmpty { "Personal Custom" },
                    difficulty = difficulty,
                    description = description.ifEmpty { "My personal created melody track." },
                    notes = songNotes
                )

                if (FirebaseService.isFirebaseAvailable.value && FirebaseService.currentFirebaseUser.value != null) {
                    FirebaseService.saveCustomSongToFirestore(customSong)
                    refreshFirestoreCustomSongs()
                } else {
                    val currentList = _customSongs.value.toMutableList()
                    currentList.add(customSong)
                    _customSongs.value = currentList
                }
            } catch (e: Exception) {
                android.util.Log.e("KeySyncViewModel", "Error adding custom song: ${e.message}", e)
            }
        }
    }

    fun importSheetMusic(bitmap: android.graphics.Bitmap, onSuccess: (com.example.api.AnalyzedSongResult) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _aiAnalyzing.value = true
            try {
                val keyToUse = _customApiKey.value.trim().ifBlank { null }
                val result = com.example.api.GeminiService.analyzeSheetMusic(bitmap, keyToUse)
                if (result != null) {
                    onSuccess(result)
                } else {
                    onError("Failed to analyze sheet music. Please check your internet connection, confirm that your API key is configured correctly in the Secrets panel, or try another image.")
                }
            } catch (e: Exception) {
                android.util.Log.e("KeySyncViewModel", "Error importing sheet music: ${e.message}", e)
                onError("Error: ${e.message}")
            } finally {
                _aiAnalyzing.value = false
            }
        }
    }

    fun importMidiFile(inputStream: java.io.InputStream, fileName: String, onSuccess: (com.example.api.AnalyzedSongResult) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            _aiAnalyzing.value = true
            try {
                val result = com.example.midi.MidiFileParser.parseMidi(inputStream, fileName)
                if (result != null) {
                    onSuccess(result)
                } else {
                    onError("Failed to parse MIDI file. Ensure it is a valid format 0 or format 1 MIDI file and has playable note events.")
                }
            } catch (e: Exception) {
                android.util.Log.e("KeySyncViewModel", "Error parsing MIDI file: ${e.message}", e)
                onError("Error: ${e.message}")
            } finally {
                _aiAnalyzing.value = false
            }
        }
    }

    fun addCustomSongWithNotes(
        title: String,
        artist: String,
        difficulty: String,
        description: String,
        notes: List<com.example.api.AnalyzedNote>
    ) {
        viewModelScope.launch {
            try {
                if (notes.isEmpty()) return@launch

                val songNotes = notes.map { note ->
                    SongNote(
                        pitch = note.pitch,
                        lyric = note.lyric,
                        frequency = getFrequencyForPitch(note.pitch)
                    )
                }

                val customSong = Song(
                    id = "custom_" + System.currentTimeMillis(),
                    title = title,
                    artist = artist.ifEmpty { "Traditional" },
                    difficulty = difficulty,
                    description = description.ifEmpty { "Transcribed from sheet music image with Gemini AI." },
                    notes = songNotes
                )

                if (FirebaseService.isFirebaseAvailable.value && FirebaseService.currentFirebaseUser.value != null) {
                    FirebaseService.saveCustomSongToFirestore(customSong)
                    refreshFirestoreCustomSongs()
                } else {
                    val currentList = _customSongs.value.toMutableList()
                    currentList.add(customSong)
                    _customSongs.value = currentList
                }
            } catch (e: Exception) {
                android.util.Log.e("KeySyncViewModel", "Error adding custom song with notes: ${e.message}", e)
            }
        }
    }

    fun deleteCustomSong(songId: String) {
        viewModelScope.launch {
            try {
                if (FirebaseService.isFirebaseAvailable.value && FirebaseService.currentFirebaseUser.value != null) {
                    FirebaseService.deleteCustomSongFromFirestore(songId)
                    refreshFirestoreCustomSongs()
                } else {
                    val currentList = _customSongs.value.filter { it.id != songId }
                    _customSongs.value = currentList
                }
            } catch (e: Exception) {
                android.util.Log.e("KeySyncViewModel", "Error deleting custom song: ${e.message}", e)
            }
        }
    }

    fun toggleFavoriteSong(songId: String) {
        viewModelScope.launch {
            try {
                val currentFavs = _favoriteSongIds.value.toMutableList()
                if (currentFavs.contains(songId)) {
                    currentFavs.remove(songId)
                } else {
                    currentFavs.add(songId)
                }
                _favoriteSongIds.value = currentFavs

                if (FirebaseService.isFirebaseAvailable.value && FirebaseService.currentFirebaseUser.value != null) {
                    FirebaseService.saveFavoritesToFirestore(currentFavs)
                }
            } catch (e: Exception) {
                android.util.Log.e("KeySyncViewModel", "Error toggling favorite: ${e.message}", e)
            }
        }
    }

    fun isSongFavorite(songId: String): Boolean {
        return _favoriteSongIds.value.contains(songId)
    }

    private fun getFrequencyForPitch(pitch: String): Float {
        return when (pitch.trim().uppercase()) {
            "C4" -> 261.63f
            "C#4", "DB4" -> 277.18f
            "D4" -> 293.66f
            "D#4", "EB4" -> 311.13f
            "E4" -> 329.63f
            "F4" -> 349.23f
            "F#4", "GB4" -> 369.99f
            "G4" -> 392.00f
            "G#4", "AB4" -> 415.30f
            "A4" -> 440.00f
            "A#4", "BB4" -> 466.16f
            "B4" -> 493.88f
            "C5" -> 523.25f
            "C#5", "DB5" -> 554.37f
            "D5" -> 587.33f
            "D#5", "EB5" -> 622.25f
            "E5" -> 659.25f
            "F5" -> 698.46f
            "F#5", "GB5" -> 739.99f
            "G5" -> 783.99f
            "G#5", "AB5" -> 830.61f
            "A5" -> 880.00f
            "A#5", "BB5" -> 932.33f
            "B5" -> 987.77f
            else -> 440.00f
        }
    }

    // Auth UI States
    private val _authStateMessage = MutableStateFlow<String?>(null)
    val authStateMessage = _authStateMessage.asStateFlow()

    private val _authIsSuccess = MutableStateFlow<Boolean?>(null)
    val authIsSuccess = _authIsSuccess.asStateFlow()

    fun clearAuthMessage() {
        _authStateMessage.value = null
        _authIsSuccess.value = null
    }

    fun register(usernameInput: String, emailInput: String, displayNameInput: String, passwordInput: String) {
        val u = usernameInput.trim().lowercase()
        val p = passwordInput.trim()
        val e = emailInput.trim()
        val d = displayNameInput.trim()

        if (p.length < 6) {
            _authStateMessage.value = "Password must be at least 6 characters"
            _authIsSuccess.value = false
            return
        }

        if (e.isEmpty() || d.isEmpty()) {
            _authStateMessage.value = "Email and Display Name cannot be empty"
            _authIsSuccess.value = false
            return
        }

        if (FirebaseService.isFirebaseAvailable.value) {
            viewModelScope.launch {
                try {
                    val fbUser = FirebaseService.signUpWithEmail(e, p, d)
                    if (fbUser != null) {
                        _authStateMessage.value = "Welcome ${fbUser.displayName ?: fbUser.email}! Firebase Registered successfully."
                        _authIsSuccess.value = true
                    } else {
                        _authStateMessage.value = "Firebase registration failed"
                        _authIsSuccess.value = false
                    }
                } catch (ex: Exception) {
                    val errMsg = ex.localizedMessage ?: ex.message ?: ""
                    if (errMsg.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true)) {
                        _authStateMessage.value = "Firebase Error: Email/Password login is not enabled in your Firebase console.\n\nTip: You can press 'Switch to Local Profile' above to create offline profiles instantly!"
                    } else {
                        _authStateMessage.value = "Firebase Error: $errMsg"
                    }
                    _authIsSuccess.value = false
                }
            }
            return
        }

        if (u.length < 3) {
            _authStateMessage.value = "Username min 3 characters required"
            _authIsSuccess.value = false
            return
        }

        viewModelScope.launch {
            val existing = repository.getUserByUsername(u)
            if (existing != null) {
                _authStateMessage.value = "Username already taken!"
                _authIsSuccess.value = false
                return@launch
            }

            val emailExisting = repository.getUserByEmail(e)
            if (emailExisting != null) {
                _authStateMessage.value = "Email address already registered!"
                _authIsSuccess.value = false
                return@launch
            }

            val newUser = User(
                username = u,
                displayName = d,
                email = e,
                passwordHash = p
            )
            val success = repository.registerUser(newUser)
            if (success) {
                _activeUser.value = newUser
                _authStateMessage.value = "Welcome ${newUser.displayName}! Registered successfully."
                _authIsSuccess.value = true
            } else {
                _authStateMessage.value = "Registration failed! Please check values."
                _authIsSuccess.value = false
            }
        }
    }

    fun login(usernameInput: String, passwordInput: String) {
        val u = usernameInput.trim()
        val p = passwordInput.trim()

        if (u.isEmpty() || p.isEmpty()) {
            _authStateMessage.value = "Please enter both credentials."
            _authIsSuccess.value = false
            return
        }

        if (FirebaseService.isFirebaseAvailable.value) {
            viewModelScope.launch {
                try {
                    val fbUser = FirebaseService.signInWithEmail(u, p)
                    if (fbUser != null) {
                        _authStateMessage.value = "Welcome back, ${fbUser.displayName ?: fbUser.email}!"
                        _authIsSuccess.value = true
                    } else {
                        _authStateMessage.value = "Firebase authentication failed"
                        _authIsSuccess.value = false
                    }
                } catch (ex: Exception) {
                    val errMsg = ex.localizedMessage ?: ex.message ?: ""
                    if (errMsg.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true)) {
                        _authStateMessage.value = "Firebase Error: Email/Password login is not enabled in your Firebase console.\n\nTip: You can press 'Switch to Local Profile' above to log in to local profiles!"
                    } else {
                        _authStateMessage.value = "Firebase Error: $errMsg"
                    }
                    _authIsSuccess.value = false
                }
            }
            return
        }

        val localUsername = u.lowercase()
        viewModelScope.launch {
            val user = repository.getUserByUsername(localUsername)
            if (user == null) {
                _authStateMessage.value = "Account not found!"
                _authIsSuccess.value = false
                return@launch
            }

            if (user.passwordHash == p) {
                _activeUser.value = user
                _authStateMessage.value = "Welcome back, ${user.displayName}!"
                _authIsSuccess.value = true
            } else {
                _authStateMessage.value = "Incorrect password!"
                _authIsSuccess.value = false
            }
        }
    }

    fun logout() {
        if (FirebaseService.isFirebaseAvailable.value) {
            FirebaseService.signOut()
            _authStateMessage.value = "Logged out of Cloud Account"
        } else {
            _activeUser.value = null
            _authStateMessage.value = "Logged out of Local Profile"
        }
        _authIsSuccess.value = null
    }

    private val audioAnalyser = AudioAnalyser()
    val isListening = audioAnalyser.isListening
    val detectedFrequency = audioAnalyser.detectedFrequency
    val detectedChord = audioAnalyser.detectedChord
    val waveform = audioAnalyser.waveform

    // MIDI helper states
    val midiIsSupported = com.example.midi.MidiService.isSupported
    val midiConnectedDeviceName = com.example.midi.MidiService.connectedDeviceName
    val midiAvailableDevices = com.example.midi.MidiService.availableDevices
    val midiActiveNotes = com.example.midi.MidiService.activeNoteNames

    // Unified notes flow: mixes ambient audio pitch tracked notes with physical MIDI device input
    val detectedNotes: StateFlow<List<String>> = kotlinx.coroutines.flow.combine(
        audioAnalyser.detectedNotes,
        com.example.midi.MidiService.activeNoteNames
    ) { audioList, midiList ->
        (audioList + midiList).distinct()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Unified single note flow: prioritizes MIDI-pressed keys, falls back to ambient microphone pitch
    val detectedNote: StateFlow<String?> = kotlinx.coroutines.flow.combine(
        audioAnalyser.detectedNote,
        com.example.midi.MidiService.activeNoteNames
    ) { audioSig, midiList ->
        midiList.firstOrNull() ?: audioSig
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun connectMidiDevice(deviceInfo: android.media.midi.MidiDeviceInfo) {
        com.example.midi.MidiService.connectDevice(deviceInfo)
    }

    fun disconnectMidiDevice() {
        com.example.midi.MidiService.disconnectCurrentDevice()
    }

    fun refreshMidiDevices() {
        com.example.midi.MidiService.refreshDevices()
    }

    // State of the Practice Track
    private val _activeSong = MutableStateFlow<Song?>(null)
    val activeSong = _activeSong.asStateFlow()

    private val _activeNoteIndex = MutableStateFlow(0)
    val activeNoteIndex = _activeNoteIndex.asStateFlow()

    private val _checkedNotes = MutableStateFlow<List<Boolean?>>(emptyList())
    val checkedNotes = _checkedNotes.asStateFlow()

    private val _isPlayingSongMode = MutableStateFlow(false)
    val isPlayingSongMode = _isPlayingSongMode.asStateFlow()

    private val _showCompletionModal = MutableStateFlow(false)
    val showCompletionModal = _showCompletionModal.asStateFlow()

    // Analytics details
    private val _finalAccuracy = MutableStateFlow(0f)
    val finalAccuracy = _finalAccuracy.asStateFlow()

    private val _finalCorrectCount = MutableStateFlow(0)
    val finalCorrectCount = _finalCorrectCount.asStateFlow()

    private val _finalTotalCount = MutableStateFlow(0)
    val finalTotalCount = _finalTotalCount.asStateFlow()

    // Recording Performance Playback States
    private val playbackPlayer = AudioPlaybackPlayer()
    private val _playingSessionId = MutableStateFlow<Int?>(null)
    val playingSessionId = _playingSessionId.asStateFlow()



    private var detectionJob: Job? = null
    private var lastMatchedTime = 0L
    private val matchCooldownMs = 1200L // Prevent double triggering on a single key stroke

    fun selectSong(song: Song) {
        stopSessionAudio() // Clear any playing reviews
        _activeSong.value = song
        _activeNoteIndex.value = 0
        _checkedNotes.value = List(song.notes.size) { null }
        _isPlayingSongMode.value = true
        _showCompletionModal.value = false
        _finalCorrectCount.value = 0
        _finalTotalCount.value = 0
        _finalAccuracy.value = 0f
        
        // The microphone starts as muted/disabled by default. The user can explicitly toggle it using the UI microphone toggle key.
        lastMatchedTime = 0L
        audioAnalyser.stopListening()
        startPitchDetectionMonitoring()
    }

    fun exitPractice() {
        _isPlayingSongMode.value = false
        _activeSong.value = null
        _activeNoteIndex.value = 0
        _checkedNotes.value = emptyList()
        detectionJob?.cancel()
        audioAnalyser.stopRecording() // Discard early recording if not completed
        audioAnalyser.stopListening()
        stopSessionAudio()
    }

    fun toggleMicrophone(requestPermissionAndStart: () -> Unit) {
        if (isListening.value) {
            audioAnalyser.stopListening()
        } else {
            requestPermissionAndStart()
        }
    }

    fun startListeningAfterPermission() {
        audioAnalyser.startListening(getApplication(), viewModelScope)
        if (_isPlayingSongMode.value) {
            audioAnalyser.startRecording()
        }
    }

    private fun startPitchDetectionMonitoring() {
        detectionJob?.cancel()
        detectionJob = viewModelScope.launch {
            audioAnalyser.detectedNotes.collect { notes ->
                if (notes.isNotEmpty() && _isPlayingSongMode.value) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastMatchedTime > matchCooldownMs) {
                        evaluatePlayedNotes(notes)
                        lastMatchedTime = currentTime
                    }
                }
            }
        }
    }

    // Force run standard pitch detection loop
    fun playSimulatedNote(note: String) {
        if (!_isPlayingSongMode.value) return
        
        // Simulating immediate note play, bypassing cooldown
        evaluatePlayedNotes(listOf(note))
        lastMatchedTime = System.currentTimeMillis()
    }

    private fun evaluatePlayedNotes(playedNotes: List<String>) {
        val song = _activeSong.value ?: return
        val currentIndex = _activeNoteIndex.value
        if (currentIndex >= song.notes.size) return

        val expectedNote = song.notes[currentIndex]
        // Match if any of the simultaneous played notes/pitches matches the target note
        val isCorrect = playedNotes.any { playedNote ->
            playedNote.uppercase().trim() == expectedNote.pitch.uppercase().trim()
        }

        val updatedChecks = _checkedNotes.value.toMutableList()
        updatedChecks[currentIndex] = isCorrect
        _checkedNotes.value = updatedChecks

        // Push to next note
        val nextIndex = currentIndex + 1
        _activeNoteIndex.value = nextIndex

        if (nextIndex >= song.notes.size) {
            viewModelScope.launch {
                delay(800) // Give short visual pause for final note evaluation before completing
                completeSession()
            }
        }
    }

    private fun completeSession() {
        val song = _activeSong.value ?: return
        val checks = _checkedNotes.value
        val correctCount = checks.count { it == true }
        val totalCount = checks.size
        val accuracy = if (totalCount > 0) (correctCount.toFloat() / totalCount) * 100f else 0f

        _finalCorrectCount.value = correctCount
        _finalTotalCount.value = totalCount
        _finalAccuracy.value = accuracy
        _showCompletionModal.value = true

        // Capitalize recording blob from AudioAnalyser
        val recordedWav = audioAnalyser.stopRecording()
        val audioFilePath = recordedWav?.let { saveRecordedAudioToFile(it) }

        // Save session history in databases
        viewModelScope.launch {
            val session = PracticeSession(
                songTitle = song.title,
                timestamp = System.currentTimeMillis(),
                accuracy = accuracy,
                correctNotes = correctCount,
                totalNotes = totalCount,
                isCompleted = true,
                audioFilePath = audioFilePath,
                username = _activeUser.value?.username
            )

            repository.insertSession(session)

            if (FirebaseService.isFirebaseAvailable.value && FirebaseService.currentFirebaseUser.value != null) {
                try {
                    FirebaseService.saveSessionToFirestore(session)
                    refreshFirestoreSessions()
                } catch (e: Exception) {
                    android.util.Log.e("KeySyncViewModel", "Error saving session to Firestore: ${e.message}", e)
                }
            }
        }

        // Stop micro listening to avoid overlapping trigger on completed screen
        audioAnalyser.stopListening()
    }

    fun resetSongPractice() {
        val song = _activeSong.value ?: return
        _activeNoteIndex.value = 0
        _checkedNotes.value = List(song.notes.size) { null }
        _showCompletionModal.value = false
        _finalCorrectCount.value = 0
        _finalTotalCount.value = 0
        _finalAccuracy.value = 0f
        lastMatchedTime = 0L

        audioAnalyser.startRecording() // Reset and restart recording
    }

    fun skipToNextNote() {
        val song = _activeSong.value ?: return
        val currentIndex = _activeNoteIndex.value
        if (currentIndex >= song.notes.size) return

        val updatedChecks = _checkedNotes.value.toMutableList()
        if (currentIndex < updatedChecks.size) {
            updatedChecks[currentIndex] = false
            _checkedNotes.value = updatedChecks
        }

        val nextIndex = currentIndex + 1
        _activeNoteIndex.value = nextIndex

        if (nextIndex >= song.notes.size) {
            viewModelScope.launch {
                delay(800)
                completeSession()
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearAll()
        }
    }

    fun dismissCompletionModal() {
        _showCompletionModal.value = false
        exitPractice()
    }

    private fun saveRecordedAudioToFile(audioData: ByteArray): String? {
        return try {
            val fileName = "practice_audio_${System.currentTimeMillis()}.wav"
            val file = File(getApplication<Application>().filesDir, fileName)
            file.writeBytes(audioData)
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Performance Custom Player Review Trigger operations
    fun playSessionAudio(session: PracticeSession) {
        val filePath = session.audioFilePath ?: return
        val currentPlaying = _playingSessionId.value
        
        if (currentPlaying == session.id) {
            stopSessionAudio()
        } else {
            stopSessionAudio()
            _playingSessionId.value = session.id
            playbackPlayer.playAudio(getApplication(), filePath) {
                _playingSessionId.value = null
            }
        }
    }

    fun stopSessionAudio() {
        playbackPlayer.stopAudio()
        _playingSessionId.value = null
    }

    override fun onCleared() {
        super.onCleared()
        audioAnalyser.stopListening()
        com.example.midi.MidiService.disconnectCurrentDevice()
        detectionJob?.cancel()
        playbackPlayer.stopAudio()
    }
}

// Helper class for playing WAV files from disk through standard MediaPlayer
class AudioPlaybackPlayer {
    private var mediaPlayer: MediaPlayer? = null

    fun playAudio(context: android.content.Context, filePath: String, onComplete: () -> Unit) {
        stopAudio()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, android.net.Uri.fromFile(java.io.File(filePath)))
                prepare()
                start()
                setOnCompletionListener {
                    onComplete()
                    stopAudio()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopAudio() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaPlayer = null
    }

    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying == true
    }
}
