package com.example.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import android.content.res.Configuration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.PracticeSession
import com.example.data.Song
import com.example.data.SongLibrary
import com.example.data.SongNote
import java.text.SimpleDateFormat
import java.util.*

// Premium Theme Colors
val ThemeDarkBg = Color(0xFF111214)
val ThemeSurface = Color(0xFF1D1E21)
val ThemeCardBg = Color(0xFF2D2F31)
val GlowCyan = Color(0xFFD0BCFF)
val GlowPurple = Color(0xFF9F8CF3)
val GlowPink = Color(0xFFFF5252)
val CleanWhite = Color(0xFFE2E2E6)
val SoftGrey = Color(0xFF9BA1A6)
val DarkGrey = Color(0xFF2D2F31)

@Composable
fun KeySyncApp(viewModel: KeySyncViewModel) {
    val context = LocalContext.current
    val isPlayingMode by viewModel.isPlayingSongMode.collectAsStateWithLifecycle()
    val showModal by viewModel.showCompletionModal.collectAsStateWithLifecycle()

    val isFirebaseAvailable by viewModel.isFirebaseAvailable.collectAsStateWithLifecycle()
    val firebaseUser by viewModel.firebaseUser.collectAsStateWithLifecycle()
    val localActiveUser by viewModel.activeUser.collectAsStateWithLifecycle()
    val isUserLoggedIn = (isFirebaseAvailable && firebaseUser != null) || (!isFirebaseAvailable && localActiveUser != null)

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startListeningAfterPermission()
        }
    }

    val requestPermissionAndStart = {
        val permissionCheck = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        )
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            viewModel.startListeningAfterPermission()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Wrap in a custom dark material context
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ThemeDarkBg)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        // Global background glass effect
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(GlowPurple.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(size.width * 0.8f, size.height * 0.2f),
                    radius = size.width * 0.6f
                )
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(GlowCyan.copy(alpha = 0.12f), Color.Transparent),
                    center = Offset(size.width * 0.2f, size.height * 0.8f),
                    radius = size.width * 0.5f
                )
            )
        }

        Crossfade(targetState = isPlayingMode, label = "ScreenTransition") { isPlaying ->
            if (isPlaying) {
                PracticeScreen(
                    viewModel = viewModel,
                    onRequestPermission = requestPermissionAndStart
                )
            } else {
                DashboardScreen(
                    viewModel = viewModel,
                    onSelectSong = { song -> viewModel.selectSong(song) },
                    onRequestPermission = requestPermissionAndStart
                )
            }
        }

        // Session Completion Modal Overlay
        if (showModal) {
            CompletionModal(viewModel = viewModel)
        }
    }
}

@Composable
fun DashboardScreen(
    viewModel: KeySyncViewModel,
    onSelectSong: (Song) -> Unit = {},
    onRequestPermission: () -> Unit
) {
    val localHistory by viewModel.sessionHistory.collectAsStateWithLifecycle()
    val isFirebaseAvailable by viewModel.isFirebaseAvailable.collectAsStateWithLifecycle()
    val firebaseUser by viewModel.firebaseUser.collectAsStateWithLifecycle()
    val firestoreSessions by viewModel.firestoreSessions.collectAsStateWithLifecycle()
    val customSongs by viewModel.customSongs.collectAsStateWithLifecycle()
    val favoriteSongIds by viewModel.favoriteSongIds.collectAsStateWithLifecycle()

    val history = if (isFirebaseAvailable && firebaseUser != null) firestoreSessions else localHistory
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val waveData by viewModel.waveform.collectAsStateWithLifecycle()
    var selectedDifficultyFilter by remember { mutableStateOf("All") }
    var showCreateSongDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        // Title Banner Section
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "KeySync Studio",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = CleanWhite,
                        fontFamily = FontFamily.SansSerif,
                        modifier = Modifier.testTag("app_title")
                    )
                    Text(
                        text = "Master your piano pitch in real-time",
                        fontSize = 14.sp,
                        color = GlowCyan,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // Mic Status Button/Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isListening) Color(0xFF1A2E22) else Color(0xFF2D2F31))
                        .border(
                            1.dp,
                            if (isListening) Color(0xFF2A4D36) else Color(0xFF3D3F41),
                            RoundedCornerShape(20.dp)
                        )
                        .clickable { viewModel.toggleMicrophone(onRequestPermission) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .testTag("mic_toggle_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isListening) Color(0xFF4ADE80) else SoftGrey)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isListening) "Listening" else "Standby",
                        color = if (isListening) Color(0xFF4ADE80) else SoftGrey,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }

        // User Authentication & Profile Insights card
        item {
            AuthProfileSection(viewModel = viewModel, history = history)
        }

        // MIDI HARDWARE KEYBOARD CONSOLE
        item {
            val midiSupported by viewModel.midiIsSupported.collectAsStateWithLifecycle()
            val midiDeviceName by viewModel.midiConnectedDeviceName.collectAsStateWithLifecycle()
            val midiDevices by viewModel.midiAvailableDevices.collectAsStateWithLifecycle()
            val midiActiveKeys by viewModel.midiActiveNotes.collectAsStateWithLifecycle()

            Card(
                colors = CardDefaults.cardColors(containerColor = ThemeSurface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(
                    1.dp,
                    if (midiDeviceName != null) GlowPurple.copy(alpha = 0.4f) else GlowCyan.copy(alpha = 0.15f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp)
                    .animateContentSize()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "🎹 USB/Bluetooth MIDI Input",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = CleanWhite
                            )
                        }
                        
                        // Status badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (!midiSupported) Color(0xFF3B1E1E)
                                    else if (midiDeviceName != null) Color(0xFF1D3221)
                                    else Color(0xFF262C3A)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (!midiSupported) "Unsupported"
                                       else if (midiDeviceName != null) "Connected"
                                       else "Scan Ready",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!midiSupported) Color(0xFFF87171)
                                        else if (midiDeviceName != null) Color(0xFF4ADE80)
                                        else GlowCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (!midiSupported) {
                        Text(
                            text = "This device's system files indicate that Android MIDI services are disabled or unavailable. Verify USB configuration.",
                            fontSize = 11.sp,
                            color = SoftGrey
                        )
                    } else {
                        if (midiDeviceName != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(DarkGrey.copy(alpha = 0.3f))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Active Hardware Controller",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = SoftGrey
                                    )
                                    Text(
                                        text = midiDeviceName ?: "MIDI Keyboard",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = CleanWhite
                                    )
                                }
                                
                                Button(
                                    onClick = { viewModel.disconnectMidiDevice() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Disconnect", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }

                            if (midiActiveKeys.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Keys Pressed: ",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GlowPurple
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = midiActiveKeys.joinToString(" , "),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Black,
                                        color = CleanWhite
                                    )
                                }
                            }
                        } else {
                            // Let user scan and connect discovered midi devices
                            Text(
                                text = "Plug in an electronic MIDI piano keyboard via USB OTG or pair via Bluetooth for the ultimate, 100% accurate note recognition experience.",
                                fontSize = 11.sp,
                                color = SoftGrey
                            )
                            
                            Spacer(modifier = Modifier.height(10.dp))

                            LaunchedEffect(Unit) {
                                viewModel.refreshMidiDevices()
                            }

                            if (midiDevices.isEmpty()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 1.5.dp,
                                            color = GlowCyan
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Scanning for external keyboards...",
                                            fontSize = 11.sp,
                                            color = SoftGrey,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                        )
                                    }
                                    
                                    IconButton(
                                        onClick = { viewModel.refreshMidiDevices() },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Refresh devices",
                                            tint = GlowCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Select Discovered Keyboard:",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GlowCyan
                                    )
                                    IconButton(
                                        onClick = { viewModel.refreshMidiDevices() },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Refresh",
                                            tint = GlowCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    midiDevices.forEach { device ->
                                        val dName = device.properties.getString(android.media.midi.MidiDeviceInfo.PROPERTY_NAME) ?: "Class Compliant Keyboard"
                                        val manufacturer = device.properties.getString(android.media.midi.MidiDeviceInfo.PROPERTY_MANUFACTURER) ?: "Generic"
                                        Button(
                                            onClick = { viewModel.connectMidiDevice(device) },
                                            colors = ButtonDefaults.buttonColors(containerColor = ThemeCardBg),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                            modifier = Modifier.fillMaxWidth().height(42.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.PlayArrow,
                                                        contentDescription = null,
                                                        tint = GlowCyan,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "$dName ($manufacturer)",
                                                        fontSize = 12.sp,
                                                        color = CleanWhite,
                                                        maxLines = 1,
                                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                    )
                                                }
                                                Text(
                                                    text = "Tap to Connect",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = GlowCyan
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(15.dp))
        }

        // Overview Statistics Summary
        item {
            if (history.isNotEmpty()) {
                val totalPracticed = history.size
                val avgAccuracy = history.map { it.accuracy }.average().toFloat()
                val totalCorrect = history.sumOf { it.correctNotes }
                val totalNotes = history.sumOf { it.totalNotes }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatCard(
                        title = "Practiced Sessions",
                        value = "$totalPracticed",
                        accentColor = GlowPurple,
                        icon = Icons.Default.History,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Avg Accuracy",
                        value = "%.1f%%".format(avgAccuracy),
                        accentColor = GlowCyan,
                        icon = Icons.Default.Star,
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(15.dp))
            }
        }

        // Song Library Headers and Filter Pills
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Songs Practice Library",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = CleanWhite
                )
                
                TextButton(
                    onClick = { showCreateSongDialog = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = GlowCyan),
                    modifier = Modifier.testTag("add_custom_song_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "Add Track", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            
            // Premium category/difficulty filter chips including favorites and custom tracks
            val categories = listOf("All", "Favorites", "My Tracks", "Easy", "Medium", "Hard")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                categories.forEach { category ->
                    val isSelected = selectedDifficultyFilter == category
                    val chipBgColor = if (isSelected) GlowCyan.copy(alpha = 0.12f) else ThemeSurface
                    val chipBorderColor = if (isSelected) GlowCyan else DarkGrey
                    val chipTextColor = if (isSelected) GlowCyan else SoftGrey
                    val chipIcon = when(category) {
                        "Favorites" -> Icons.Default.Favorite
                        "My Tracks" -> Icons.Default.Create
                        "Easy" -> Icons.Default.CheckCircle
                        "Medium" -> Icons.Default.Star
                        "Hard" -> Icons.Default.Whatshot
                        else -> Icons.Default.MusicNote
                    }
                    val chipIconColor = if (isSelected) {
                        GlowCyan
                    } else {
                        when (category) {
                            "Favorites" -> GlowPink
                            "My Tracks" -> GlowCyan
                            else -> SoftGrey
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(chipBgColor)
                            .border(1.dp, chipBorderColor, RoundedCornerShape(12.dp))
                            .clickable { selectedDifficultyFilter = category }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("filter_chip_$category")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = chipIcon,
                                contentDescription = null,
                                tint = chipIconColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = category,
                                color = chipTextColor,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }

        // Categorized songs rendering incorporating presets + custom songs + favorites
        val allSongsList = SongLibrary.songs + customSongs
        val groupedSongs = allSongsList.groupBy { it.difficulty }

        val displaySections = when (selectedDifficultyFilter) {
            "All" -> listOf(
                "My Tracks" to customSongs,
                "Favorites" to allSongsList.filter { viewModel.isSongFavorite(it.id) },
                "Easy" to (groupedSongs["Easy"] ?: emptyList()),
                "Medium" to (groupedSongs["Medium"] ?: emptyList()),
                "Hard" to (groupedSongs["Hard"] ?: emptyList())
            )
            "Favorites" -> listOf(
                "Favorites" to allSongsList.filter { viewModel.isSongFavorite(it.id) }
            )
            "My Tracks" -> listOf(
                "My Tracks" to customSongs
            )
            else -> listOf(
                selectedDifficultyFilter to (groupedSongs[selectedDifficultyFilter] ?: emptyList())
            )
        }

        displaySections.forEach { (sectionTitle, songsInSection) ->
            if (songsInSection.isNotEmpty()) {
                item {
                    val colorAccent = when (sectionTitle) {
                        "Favorites" -> GlowPink
                        "My Tracks" -> GlowCyan
                        "Easy" -> Color(0xFF4ADE80)
                        "Medium" -> GlowPurple
                        else -> GlowPink
                    }
                    val icon = when (sectionTitle) {
                        "Favorites" -> Icons.Default.Favorite
                        "My Tracks" -> Icons.Default.Create
                        "Easy" -> Icons.Default.CheckCircle
                        "Medium" -> Icons.Default.Star
                        else -> Icons.Default.Whatshot
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = colorAccent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "$sectionTitle (${songsInSection.size})",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorAccent,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                items(songsInSection, key = { it.id }) { song ->
                    val isCustom = customSongs.any { it.id == song.id }
                    SongListItem(
                        song = song,
                        isFavorite = viewModel.isSongFavorite(song.id),
                        onFavoriteToggle = { viewModel.toggleFavoriteSong(song.id) },
                        onDeleteClick = if (isCustom) {
                            { viewModel.deleteCustomSong(song.id) }
                        } else null,
                        onClick = { onSelectSong(song) }
                    )
                }
            }
        }

        // Session History
        if (history.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(28.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Practice Logs",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = CleanWhite
                    )
                    Text(
                        text = "Clear All",
                        color = GlowPink,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable { viewModel.clearHistory() }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            items(history) { session ->
                HistoryRow(session = session, viewModel = viewModel)
            }
        } else {
            item {
                Spacer(modifier = Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ThemeSurface, RoundedCornerShape(12.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "No sessions",
                            tint = SoftGrey,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No practice logs yet. Start practicing dynamic tracks with your piano or on-screen keys!",
                            textAlign = TextAlign.Center,
                            color = SoftGrey,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }

    if (showCreateSongDialog) {
        CreateSongDialog(
            onDismiss = { showCreateSongDialog = false },
            onSave = { title, artist, difficulty, description, notes ->
                viewModel.addCustomSong(title, artist, difficulty, description, notes)
            }
        )
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    accentColor: Color,
    imageVector: androidx.compose.ui.graphics.vector.ImageVector? = null,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ThemeSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.15f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontSize = 12.sp, color = SoftGrey, fontWeight = FontWeight.Medium)
                icon?.let {
                    Icon(imageVector = it, contentDescription = title, tint = accentColor, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = CleanWhite
            )
        }
    }
}

@Composable
fun SongListItem(
    song: Song,
    isFavorite: Boolean,
    onFavoriteToggle: () -> Unit,
    onDeleteClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val badgeColor = when (song.difficulty) {
        "Easy" -> Color(0xFF4ADE80) // Emerald Green
        "Medium" -> GlowPurple       // Violet/Purple
        else -> GlowPink             // Warm Pink/Red
    }
    
    val badgeIcon = when (song.difficulty) {
        "Easy" -> Icons.Default.CheckCircle
        "Medium" -> Icons.Default.Star
        else -> Icons.Default.Whatshot
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = ThemeSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, DarkGrey),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clickable { onClick() }
            .testTag("song_item_${song.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(badgeColor.copy(alpha = 0.12f))
                            .border(1.dp, badgeColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = badgeIcon,
                                contentDescription = null,
                                tint = badgeColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = song.difficulty.uppercase(),
                                color = badgeColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "by ${song.artist}", color = SoftGrey, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = song.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = CleanWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = song.description,
                    fontSize = 12.sp,
                    color = SoftGrey,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Delete button for custom songs
                if (onDeleteClick != null) {
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("delete_song_${song.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete track",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Heart/Favorite button
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("favorite_song_${song.id}")
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite track",
                        tint = if (isFavorite) GlowPink else SoftGrey,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Play button
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(GlowCyan.copy(alpha = 0.08f))
                        .border(1.dp, GlowCyan.copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start practice",
                        tint = GlowCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryRow(session: PracticeSession, viewModel: KeySyncViewModel) {
    val dateString = remember(session.timestamp) {
        val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        sdf.format(Date(session.timestamp))
    }

    val playingSessionId by viewModel.playingSessionId.collectAsStateWithLifecycle()
    val isPlayingThis = playingSessionId == session.id

    Card(
        colors = CardDefaults.cardColors(containerColor = ThemeSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        border = BorderStroke(1.dp, DarkGrey)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = session.songTitle, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CleanWhite)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (session.audioFilePath != null) {
                        "$dateString • ${session.correctNotes}/${session.totalNotes} Notes • Review Clip"
                    } else {
                        "$dateString • ${session.correctNotes}/${session.totalNotes} Notes"
                    },
                    fontSize = 11.sp,
                    color = SoftGrey
                )
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (session.audioFilePath != null) {
                    IconButton(
                        onClick = { viewModel.playSessionAudio(session) },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isPlayingThis) GlowPink.copy(alpha = 0.2f) else GlowCyan.copy(alpha = 0.15f))
                            .border(1.dp, if (isPlayingThis) GlowPink else GlowCyan.copy(alpha = 0.4f), CircleShape)
                            .testTag("action_review_play_${session.id}")
                    ) {
                        Icon(
                            imageVector = if (isPlayingThis) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPlayingThis) "Stop audio review" else "Play performance review",
                            tint = if (isPlayingThis) GlowPink else GlowCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (session.accuracy >= 80f) GlowCyan.copy(alpha = 0.1f) else DarkGrey
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "%.0f%%".format(session.accuracy),
                        color = if (session.accuracy >= 80f) GlowCyan else CleanWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun PracticeScreen(
    viewModel: KeySyncViewModel,
    onRequestPermission: () -> Unit
) {
    val activeSong by viewModel.activeSong.collectAsStateWithLifecycle()
    val activeNoteIndex by viewModel.activeNoteIndex.collectAsStateWithLifecycle()
    val checkedNotes by viewModel.checkedNotes.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val detectedNote by viewModel.detectedNote.collectAsStateWithLifecycle()
    val detectedFrequency by viewModel.detectedFrequency.collectAsStateWithLifecycle()
    val detectedNotes by viewModel.detectedNotes.collectAsStateWithLifecycle()
    val detectedChord by viewModel.detectedChord.collectAsStateWithLifecycle()
    val waveData by viewModel.waveform.collectAsStateWithLifecycle()

    val song = activeSong ?: return

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (!isLandscape) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // TOP NAVIGATION BAR
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = { viewModel.exitPractice() },
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Exit to Library",
                            tint = CleanWhite
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            color = CleanWhite,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(text = "Practice Track", color = GlowCyan, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                    }

                    // Microphone quick toggle key
                    IconButton(
                        onClick = { viewModel.toggleMicrophone(onRequestPermission) }
                    ) {
                        Icon(
                            imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = "Microphone toggle",
                            tint = if (isListening) GlowCyan else GlowPink
                        )
                    }
                }

                // Real-time Pitch Detection Panel
                Card(
                    colors = CardDefaults.cardColors(containerColor = ThemeSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .border(BorderStroke(1.dp, GlowCyan.copy(alpha = 0.15f)), RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "Polyphonic Pitch & Chord Recognizer", fontSize = 11.sp, color = SoftGrey, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = detectedNote ?: "---",
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (detectedNote != null) GlowCyan else SoftGrey
                                )
                                if (detectedFrequency > 0f) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "%.0f Hz".format(detectedFrequency),
                                        fontSize = 12.sp,
                                        color = SoftGrey,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                }
                            }

                            // Display polyphonic notes lists and recognized chord classes
                            if (detectedNotes.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // List of simultaneous notes
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(GlowCyan.copy(alpha = 0.12f))
                                            .border(1.dp, GlowCyan.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (detectedNotes.size > 1) {
                                                detectedNotes.joinToString(" • ")
                                            } else {
                                                detectedNotes.first()
                                            },
                                            color = GlowCyan,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Recognized chord tag
                                    detectedChord?.let { chord ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(GlowPurple.copy(alpha = 0.15f))
                                                .border(1.dp, GlowPurple.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.MusicNote,
                                                    contentDescription = null,
                                                    tint = GlowPurple,
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                Text(
                                                    text = chord,
                                                    color = GlowPurple,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Compact listening waveform
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.width(130.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isListening) GlowCyan else DarkGrey)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isListening) "LISTENING" else "MIC MUTED",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isListening) GlowCyan else GlowPink
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))

                            // Wave animation line
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(20.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val sublist = if (isListening) waveData.take(15) else List(15) { 0.1f }
                                sublist.forEach { amp ->
                                    val h = (amp * 25f).coerceAtLeast(2f)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(h.dp)
                                            .background(
                                                if (isListening) GlowCyan else DarkGrey,
                                                RoundedCornerShape(1.dp)
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // STEP-BY-STEP HORIZONTAL SCROLLING NOTES TRACK
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            ) {
                val widthDp = maxWidth
                val noteWidth = 100.dp
                val noteSpacing = 16.dp
                val sidePadding = (widthDp - noteWidth) / 2
                
                val density = androidx.compose.ui.platform.LocalDensity.current
                val scrollState = rememberScrollState()
                
                // Auto scroll container to center the active note
                LaunchedEffect(activeNoteIndex) {
                    if (activeNoteIndex >= 0) {
                        val itemWidthPx = with(density) { (noteWidth + noteSpacing).toPx() }
                        val targetPx = (activeNoteIndex * itemWidthPx).toInt()
                        scrollState.animateScrollTo(targetPx)
                    }
                }

                Column {
                    Text(
                        text = "SCROLLING NOTES TRACK",
                        fontSize = 10.sp,
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold,
                        color = SoftGrey,
                        modifier = Modifier.padding(start = 20.dp, bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(ThemeSurface)
                            .drawBehind {
                                // Drawing professional music staff guidelines behind horizontal notes for realism!
                                val staffSpacing = 16.dp.toPx()
                                val centerY = size.height / 2
                                for (i in -2..2) {
                                    val lineY = centerY + (i * staffSpacing)
                                    drawLine(
                                        color = DarkGrey.copy(alpha = 0.4f),
                                        start = Offset(0f, lineY),
                                        end = Offset(size.width, lineY),
                                        strokeWidth = 1.5.dp.toPx()
                                    )
                                }
                            }
                            .horizontalScroll(scrollState)
                            .padding(horizontal = sidePadding), // Balanced responsive padding centers active note in middle
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(noteSpacing)
                    ) {
                        song.notes.forEachIndexed { index, songNote ->
                            val isActive = index == activeNoteIndex
                            val isPast = index < activeNoteIndex
                            val evaluationResult = checkedNotes.getOrNull(index)

                            NoteTrackItem(
                                note = songNote,
                                isActive = isActive,
                                isPast = isPast,
                                evaluationResult = evaluationResult
                            )
                        }
                    }
                }
            }

            // BOTTOM CONTROLS & ANALYTICS BAR (Elegant Dark Specifications)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Analytics Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ThemeSurface, RoundedCornerShape(16.dp))
                        .border(BorderStroke(1.dp, Color(0xFF2D2F31)), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "ACCURACY",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = SoftGrey
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val currentPlayed = checkedNotes.count { it != null }
                        val correctNotesCount = checkedNotes.count { it == true }
                        val accuracyPercent = if (currentPlayed > 0) (correctNotesCount * 100 / currentPlayed) else 100
                        Text(
                            text = "$accuracyPercent%",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlowCyan
                        )
                    }

                    // Divider line
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(32.dp)
                            .background(Color(0xFF2D2F31))
                    )

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "PROGRESS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = SoftGrey
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$activeNoteIndex / ${song.notes.size}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = CleanWhite
                        )
                    }
                }

                // Main Actions Control row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Replay/Reset Action Button
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Transparent)
                            .border(BorderStroke(1.dp, Color(0xFF2D2F31)), CircleShape)
                            .clickable { viewModel.resetSongPractice() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset song",
                            tint = CleanWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(32.dp))

                    // Main Action - Pause/Resume/Play (Exit practice is our main toggle back, or simulated check)
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(GlowCyan)
                            .clickable { viewModel.exitPractice() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = "Pause and exit",
                            tint = ThemeDarkBg,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(32.dp))

                    // Skip Next Action Button
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Transparent)
                            .border(BorderStroke(1.dp, Color(0xFF2D2F31)), CircleShape)
                            .clickable {
                                viewModel.skipToNextNote()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Skip to next note",
                            tint = CleanWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // INTERACTIVE PIANO TRAINING KEYS
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ON-SCREEN HELPER KEYBOARD (TAP TO SIMULATE PLAY)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = SoftGrey,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                val whiteKeys = listOf("C4", "D4", "E4", "F4", "G4", "A4", "B4", "C5")
                
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.TopStart
                ) {
                    val totalWidth = maxWidth
                    val whiteKeyWidth = totalWidth / 8
                    val blackKeyWidth = whiteKeyWidth * 0.6f

                    // 1. White keys
                    Row(modifier = Modifier.fillMaxSize()) {
                        whiteKeys.forEach { key ->
                            val isExpected = song.notes.getOrNull(activeNoteIndex)?.pitch == key
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(horizontal = 2.dp)
                                    .background(
                                        if (isExpected) GlowCyan.copy(alpha = 0.28f) else CleanWhite,
                                        RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                                    )
                                    .border(
                                        if (isExpected) 2.dp else 1.dp,
                                        if (isExpected) GlowCyan else SoftGrey,
                                        RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
                                    )
                                    .clickable { viewModel.playSimulatedNote(key) }
                                    .padding(bottom = 12.dp),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Text(
                                    text = key,
                                    color = if (isExpected) GlowCyan else Color.DarkGray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                    }

                    // 2. Black keys position centered on White Key boundaries
                    val blackKeyPitches = listOf("C#4", "D#4", "F#4", "G#4", "A#4")
                    val blackKeyIndexes = listOf(1, 2, 4, 5, 6)

                    blackKeyPitches.forEachIndexed { idx, blackKey ->
                        val boundaryIdx = blackKeyIndexes[idx]
                        val isExpected = song.notes.getOrNull(activeNoteIndex)?.pitch == blackKey
                        val xOffset = (whiteKeyWidth * boundaryIdx) - (blackKeyWidth / 2)

                        Box(
                            modifier = Modifier
                                .offset(x = xOffset)
                                .width(blackKeyWidth)
                                .fillMaxHeight(0.6f)
                                .background(
                                    if (isExpected) GlowPurple.copy(alpha = 0.8f) else DarkGrey,
                                    RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
                                )
                                .border(
                                    if (isExpected) 2.dp else 1.dp,
                                    if (isExpected) GlowCyan else Color.Black,
                                    RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
                                )
                                .clickable { viewModel.playSimulatedNote(blackKey) }
                                .padding(bottom = 6.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Text(
                                text = blackKey.replace("4", ""),
                                color = CleanWhite,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // LEFT PANE (Controls, Info & Pitch/Chord Detection) - width 300.dp
            Column(
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .padding(10.dp)
                    .background(ThemeSurface, RoundedCornerShape(14.dp))
                    .border(BorderStroke(1.dp, Color(0xFF2D2F31)), RoundedCornerShape(14.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header + Toggle Mic
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { viewModel.exitPractice() },
                            modifier = Modifier
                                .testTag("back_button")
                                .size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Exit to Library",
                                tint = CleanWhite,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                color = CleanWhite,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(text = "Practice Track", color = GlowCyan, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                        }

                        IconButton(
                            onClick = { viewModel.toggleMicrophone(onRequestPermission) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicOff,
                                contentDescription = "Microphone toggle",
                                tint = if (isListening) GlowCyan else GlowPink,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Pitch & Chord Recognizer Card
                    Column {
                        Text(text = "PITCH & CHORD RECOGNIZER", fontSize = 9.sp, color = SoftGrey, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = detectedNote ?: "---",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (detectedNote != null) GlowCyan else SoftGrey
                                )
                                if (detectedFrequency > 0f) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "%.0f Hz".format(detectedFrequency),
                                        fontSize = 11.sp,
                                        color = SoftGrey
                                    )
                                }
                            }

                            // Compact wave display
                            Row(
                                modifier = Modifier
                                    .width(60.dp)
                                    .height(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val sublist = if (isListening) waveData.take(10) else List(10) { 0.1f }
                                sublist.forEach { amp ->
                                    val h = (amp * 16f).coerceAtLeast(2f)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(h.dp)
                                            .background(
                                                if (isListening) GlowCyan else DarkGrey,
                                                RoundedCornerShape(1.dp)
                                            )
                                    )
                                }
                            }
                        }

                        if (detectedNotes.isNotEmpty() || detectedChord != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (detectedNotes.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(GlowCyan.copy(alpha = 0.12f))
                                            .border(1.dp, GlowCyan.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (detectedNotes.size > 1) {
                                                detectedNotes.joinToString("•")
                                            } else {
                                                detectedNotes.first()
                                            },
                                            color = GlowCyan,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                detectedChord?.let { chord ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(GlowPurple.copy(alpha = 0.15f))
                                            .border(1.dp, GlowPurple.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = null,
                                                tint = GlowPurple,
                                                modifier = Modifier.size(9.dp)
                                            )
                                            Text(
                                                text = chord,
                                                color = GlowPurple,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Controls & Analytics
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ThemeCardBg, RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(text = "ACCURACY", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = SoftGrey)
                            val currentPlayed = checkedNotes.count { it != null }
                            val correctNotesCount = checkedNotes.count { it == true }
                            val accuracyPercent = if (currentPlayed > 0) (correctNotesCount * 100 / currentPlayed) else 100
                            Text(text = "$accuracyPercent%", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = GlowCyan)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(text = "PROGRESS", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = SoftGrey)
                            Text(text = "$activeNoteIndex / ${song.notes.size}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CleanWhite)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Reset
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .border(BorderStroke(1.dp, Color(0xFF424446)), CircleShape)
                                .clickable { viewModel.resetSongPractice() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reset song",
                                tint = CleanWhite,
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // Exit/Pause
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(GlowCyan)
                                .clickable { viewModel.exitPractice() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = "Pause and exit",
                                tint = ThemeDarkBg,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Skip
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .border(BorderStroke(1.dp, Color(0xFF424446)), CircleShape)
                                .clickable { viewModel.skipToNextNote() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Skip note",
                                tint = CleanWhite,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // RIGHT PANE (Scrolling Track & Helper Keyboard side-by-side or stacked on the rest of screen)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 1. SCROLLING NOTES TRACK
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.48f)
                        .padding(vertical = 4.dp)
                ) {
                    val widthDp = maxWidth
                    val noteWidth = 100.dp
                    val noteSpacing = 12.dp
                    val sidePadding = (widthDp - noteWidth) / 2
                    
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val scrollState = rememberScrollState()
                    
                    LaunchedEffect(activeNoteIndex) {
                        if (activeNoteIndex >= 0) {
                            val itemWidthPx = with(density) { (noteWidth + noteSpacing).toPx() }
                            val targetPx = (activeNoteIndex * itemWidthPx).toInt()
                            scrollState.animateScrollTo(targetPx)
                        }
                    }

                    Column(modifier = Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .background(ThemeSurface)
                                .drawBehind {
                                    val staffSpacing = 12.dp.toPx()
                                    val centerY = size.height / 2
                                    for (i in -2..2) {
                                        val lineY = centerY + (i * staffSpacing)
                                        drawLine(
                                            color = DarkGrey.copy(alpha = 0.35f),
                                            start = Offset(0f, lineY),
                                            end = Offset(size.width, lineY),
                                            strokeWidth = 1.2.dp.toPx()
                                        )
                                    }
                                }
                                .horizontalScroll(scrollState)
                                .padding(horizontal = sidePadding),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(noteSpacing)
                        ) {
                            song.notes.forEachIndexed { index, songNote ->
                                val isActive = index == activeNoteIndex
                                val isPast = index < activeNoteIndex
                                val evaluationResult = checkedNotes.getOrNull(index)

                                NoteTrackItem(
                                    note = songNote,
                                    isActive = isActive,
                                    isPast = isPast,
                                    evaluationResult = evaluationResult
                                )
                            }
                        }
                    }
                }

                // 2. INTERACTIVE PIANO KEYBOARD
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.52f)
                        .padding(bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val whiteKeys = listOf("C4", "D4", "E4", "F4", "G4", "A4", "B4", "C5")
                    
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.TopStart
                    ) {
                        val totalWidth = maxWidth
                        val whiteKeyWidth = totalWidth / 8
                        val blackKeyWidth = whiteKeyWidth * 0.6f

                        // 1. White keys
                        Row(modifier = Modifier.fillMaxSize()) {
                            whiteKeys.forEach { key ->
                                val isExpected = song.notes.getOrNull(activeNoteIndex)?.pitch == key
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(horizontal = 1.dp)
                                        .background(
                                            if (isExpected) GlowCyan.copy(alpha = 0.28f) else CleanWhite,
                                            RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)
                                        )
                                        .border(
                                            if (isExpected) 2.dp else 1.dp,
                                            if (isExpected) GlowCyan else SoftGrey,
                                            RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)
                                        )
                                        .clickable { viewModel.playSimulatedNote(key) }
                                        .padding(bottom = 6.dp),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    Text(
                                        text = key,
                                        color = if (isExpected) GlowCyan else Color.DarkGray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                            }
                        }

                        // 2. Black keys centered on White Key boundaries
                        val blackKeyPitches = listOf("C#4", "D#4", "F#4", "G#4", "A#4")
                        val blackKeyIndexes = listOf(1, 2, 4, 5, 6)

                        blackKeyPitches.forEachIndexed { idx, blackKey ->
                            val boundaryIdx = blackKeyIndexes[idx]
                            val isExpected = song.notes.getOrNull(activeNoteIndex)?.pitch == blackKey
                            val xOffset = (whiteKeyWidth * boundaryIdx) - (blackKeyWidth / 2)

                            Box(
                                modifier = Modifier
                                    .offset(x = xOffset)
                                    .width(blackKeyWidth)
                                    .fillMaxHeight(0.58f)
                                    .background(
                                        if (isExpected) GlowPurple.copy(alpha = 0.85f) else DarkGrey,
                                        RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp)
                                    )
                                    .border(
                                        if (isExpected) 2.dp else 1.dp,
                                        if (isExpected) GlowCyan else Color.Black,
                                        RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp)
                                    )
                                    .clickable { viewModel.playSimulatedNote(blackKey) }
                                    .padding(bottom = 2.dp),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Text(
                                    text = blackKey.replace("4", ""),
                                    color = CleanWhite,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NoteTrackItem(
    note: SongNote,
    isActive: Boolean,
    isPast: Boolean,
    evaluationResult: Boolean?
) {
    // Dynamic animations for current focus note
    val infiniteTransition = rememberInfiniteTransition(label = "PulseEffect")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val currentScale = if (isActive) pulseScale else 1.0f

    val noteWidth = if (isActive) 80.dp else 64.dp
    val noteHeight = if (isActive) 100.dp else 80.dp

    Column(
        modifier = Modifier
            .width(100.dp)
            .wrapContentHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Notation card
        Box(
            modifier = Modifier
                .width(noteWidth * currentScale)
                .height(noteHeight * currentScale)
                .background(
                    when {
                        isActive -> GlowCyan
                        else -> Color(0xFF2D2F31)
                    },
                    shape = RoundedCornerShape(16.dp)
                )
                .border(
                    width = if (isActive) 2.dp else 1.dp,
                    color = when {
                        isActive -> GlowCyan.copy(alpha = 0.5f)
                        else -> Color(0xFF3D3F41)
                    },
                    shape = RoundedCornerShape(16.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = note.pitch,
                    color = when {
                        isActive -> ThemeDarkBg
                        isPast && evaluationResult == true -> Color(0xFF4ADE80)
                        isPast && evaluationResult == false -> Color(0xFFFF5252)
                        else -> CleanWhite
                    },
                    fontSize = if (isActive) 22.sp else 18.sp,
                    fontWeight = FontWeight.Bold
                )

                // Validation micro badges
                if (isPast) {
                    Spacer(modifier = Modifier.height(2.dp))
                    if (evaluationResult == true) {
                        Text(text = "✓", color = Color(0xFF4ADE80), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    } else {
                        Text(text = "✕", color = Color(0xFFFF5252), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Lyric sync display
        Text(
            text = note.lyric,
            color = if (isActive) GlowCyan else SoftGrey,
            fontSize = if (isActive) 14.sp else 12.sp,
            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        if (isActive) {
            Text(
                text = "PLAY NOW",
                color = GlowCyan,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun CompletionModal(viewModel: KeySyncViewModel) {
    val accuracy by viewModel.finalAccuracy.collectAsStateWithLifecycle()
    val correctCount by viewModel.finalCorrectCount.collectAsStateWithLifecycle()
    val totalCount by viewModel.finalTotalCount.collectAsStateWithLifecycle()
    val song by viewModel.activeSong.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ThemeSurface),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(BorderStroke(1.5.dp, GlowCyan), RoundedCornerShape(24.dp))
                .testTag("completion_modal")
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Trophy/Badge drawing
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(GlowCyan.copy(alpha = 0.15f))
                        .border(1.5.dp, GlowCyan, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Session complete motif",
                        tint = GlowCyan,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Session Completed!",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = CleanWhite
                )

                Text(
                    text = song?.title ?: "Classics Practiced",
                    fontSize = 14.sp,
                    color = SoftGrey,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Big Dial Statistics Indicator
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ThemeDarkBg, RoundedCornerShape(16.dp))
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "%.0f%%".format(accuracy),
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Black,
                        color = if (accuracy >= 80f) GlowCyan else GlowPink
                    )
                    Text(
                        text = "Accuracy Percentage",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SoftGrey
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = DarkGrey)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "$correctCount of $totalCount Notes Played Correctly",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = CleanWhite
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    val verdict = when {
                        accuracy >= 90f -> "Pristine Mastery! Perfect pitch!"
                        accuracy >= 75f -> "Brilliant work! You are getting close!"
                        else -> "Keep practiced! Repetition builds perfection."
                    }
                    Text(
                        text = verdict,
                        fontSize = 12.sp,
                        color = GlowCyan,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                Button(
                    onClick = { viewModel.dismissCompletionModal() },
                    colors = ButtonDefaults.buttonColors(containerColor = GlowCyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("dismiss_modal_button")
                ) {
                    Text(
                        text = "Return to Song Library",
                        color = ThemeDarkBg,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun AuthProfileSection(
    viewModel: KeySyncViewModel,
    history: List<PracticeSession>
) {
    val isFirebaseAvailable by viewModel.isFirebaseAvailable.collectAsStateWithLifecycle()
    val firebaseUser by viewModel.firebaseUser.collectAsStateWithLifecycle()
    val localActiveUser by viewModel.activeUser.collectAsStateWithLifecycle()

    val isUserLoggedIn = (isFirebaseAvailable && firebaseUser != null) || (!isFirebaseAvailable && localActiveUser != null)

    if (!isUserLoggedIn) return // Under our strict mandatory login setup, this shouldn't render if not logged in.

    Card(
        colors = CardDefaults.cardColors(containerColor = ThemeSurface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = GlowPurple.copy(alpha = 0.4f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .animateContentSize()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Connection status banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isFirebaseAvailable) Color(0xFF132F21) else Color(0xFF332015))
                    .border(
                        1.dp,
                        if (isFirebaseAvailable) Color(0xFF234B33) else Color(0xFF4B3121),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isFirebaseAvailable) Color(0xFF4ADE80) else Color(0xFFFB923C))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isFirebaseAvailable) "Firebase Cloud Sync Connected" else "Local Sandbox Practice Mode",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isFirebaseAvailable) Color(0xFF4ADE80) else Color(0xFFFB923C)
                    )
                }

                if (com.example.data.FirebaseService.isFirebaseConfigured()) {
                    TextButton(
                        onClick = {
                            viewModel.toggleFirebaseMode()
                            viewModel.clearAuthMessage()
                        },
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("auth_toggle_cloud_local_button")
                    ) {
                        Text(
                            text = if (isFirebaseAvailable) "Use Local Mode" else "Use Cloud Sync",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isFirebaseAvailable) Color(0xFFFB923C) else Color(0xFF4ADE80)
                        )
                    }
                }
            }

            // Logged In User State Profile Card
            val userDisplayName = if (isFirebaseAvailable) {
                firebaseUser?.displayName ?: firebaseUser?.email?.substringBefore("@") ?: "Cloud Artist"
            } else {
                localActiveUser?.displayName ?: "Local Artist"
            }

            val userEmailOrSub = if (isFirebaseAvailable) {
                firebaseUser?.email ?: "cloud@sync.com"
            } else {
                "@${localActiveUser?.username} • ${localActiveUser?.email}"
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val avatarColors = listOf(Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFF10B981), Color(0xFF3B82F6))
                    val bgIndex = Math.abs(userDisplayName.hashCode()) % avatarColors.size
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(avatarColors[bgIndex]),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = userDisplayName.take(1).uppercase(),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = userDisplayName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = CleanWhite
                        )
                        Text(
                            text = userEmailOrSub,
                            fontSize = 11.sp,
                            color = SoftGrey
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = DarkGrey.copy(alpha = 0.5f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Personalized dynamic stats derived from database
            Text(
                text = "Personal Practice Insights",
                fontSize = 11.sp,
                color = GlowPurple,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(bottom = 10.dp)
            )

            val sessionCount = history.size
            val avgAccuracy = if (history.isNotEmpty()) {
                history.map { it.accuracy }.average().toFloat()
            } else {
                0f
            }
            val completedCount = history.count { it.isCompleted }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkGrey.copy(alpha = 0.4f))
                        .padding(8.dp)
                ) {
                    Column {
                        Text(text = "Tracks", fontSize = 10.sp, color = SoftGrey)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = sessionCount.toString(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = CleanWhite
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkGrey.copy(alpha = 0.4f))
                        .padding(8.dp)
                ) {
                    Column {
                        Text(text = "Avg Acc", fontSize = 10.sp, color = SoftGrey)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "%.1f%%".format(avgAccuracy),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (avgAccuracy >= 80f) Color(0xFF4ADE80) else CleanWhite
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkGrey.copy(alpha = 0.4f))
                        .padding(8.dp)
                ) {
                    Column {
                        Text(text = "Completed", fontSize = 10.sp, color = SoftGrey)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = completedCount.toString(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlowCyan
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CreateSongDialog(
    onDismiss: () -> Unit,
    onSave: (title: String, artist: String, difficulty: String, description: String, notes: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var difficulty by remember { mutableStateOf("Easy") }
    var description by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("C4, E4, G4") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Create Custom Piano Track",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = CleanWhite
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it; errorMsg = null },
                    label = { Text("Song Title*", color = SoftGrey) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GlowCyan,
                        focusedLabelColor = GlowCyan,
                        unfocusedBorderColor = DarkGrey,
                        focusedTextColor = CleanWhite,
                        unfocusedTextColor = CleanWhite
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("custom_song_title_input")
                )

                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist (Optional)", color = SoftGrey) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GlowCyan,
                        focusedLabelColor = GlowCyan,
                        unfocusedBorderColor = DarkGrey,
                        focusedTextColor = CleanWhite,
                        unfocusedTextColor = CleanWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Column {
                    Text("Difficulty Level", fontSize = 12.sp, color = SoftGrey, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Easy", "Medium", "Hard").forEach { diff ->
                            val isSelected = difficulty == diff
                            val dColor = when (diff) {
                                "Easy" -> Color(0xFF4ADE80)
                                "Medium" -> GlowPurple
                                else -> GlowPink
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) dColor.copy(alpha = 0.15f) else ThemeSurface)
                                    .border(1.dp, if (isSelected) dColor else DarkGrey, RoundedCornerShape(8.dp))
                                    .clickable { difficulty = diff }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = diff,
                                    color = if (isSelected) dColor else SoftGrey,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it; errorMsg = null },
                    label = { Text("Note Pitches (comma separated)*", color = SoftGrey) },
                    placeholder = { Text("e.g., C4, E4, G4, C5", color = SoftGrey) },
                    supportingText = {
                        Text(
                            text = "Standard Piano Pitches from C4 to B5 are supported. e.g. C4, D4, E4, F4, G4, A4, B4, C5.",
                            fontSize = 10.sp,
                            color = SoftGrey
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GlowCyan,
                        focusedLabelColor = GlowCyan,
                        unfocusedBorderColor = DarkGrey,
                        focusedTextColor = CleanWhite,
                        unfocusedTextColor = CleanWhite
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("custom_song_notes_input")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Brief Description / Tip", color = SoftGrey) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GlowCyan,
                        focusedLabelColor = GlowCyan,
                        unfocusedBorderColor = DarkGrey,
                        focusedTextColor = CleanWhite,
                        unfocusedTextColor = CleanWhite
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = Color(0xFFEF4444),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.trim().isEmpty()) {
                        errorMsg = "Please enter a song title"
                        return@Button
                    }
                    if (notes.trim().isEmpty()) {
                        errorMsg = "Please enter pitch notes"
                        return@Button
                    }
                    onSave(title.trim(), artist.trim(), difficulty, description.trim(), notes.trim())
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = GlowCyan),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("save_custom_song_confirm")
            ) {
                Text("Save Track", color = ThemeDarkBg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = SoftGrey)
            ) {
                Text("Cancel")
            }
        },
        containerColor = ThemeSurface,
        shape = RoundedCornerShape(16.dp),
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = true)
    )
}

@Composable
fun LoginScreen(viewModel: KeySyncViewModel) {
    val isFirebaseAvailable by viewModel.isFirebaseAvailable.collectAsStateWithLifecycle()
    val authMessage by viewModel.authStateMessage.collectAsStateWithLifecycle()
    val authSuccess by viewModel.authIsSuccess.collectAsStateWithLifecycle()

    var usernameOrEmail by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = ThemeSurface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, GlowPurple.copy(alpha = 0.25f)),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .padding(vertical = 16.dp)
                .animateContentSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // KeySync Brand Logo Header
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(GlowPurple.copy(alpha = 0.15f))
                        .border(2.dp, GlowPurple, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "KeySync Brand Music Note Logo",
                        tint = GlowCyan,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "KeySync Studio",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = CleanWhite,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Authorization Required",
                    fontSize = 13.sp,
                    color = GlowCyan,
                    modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
                )

                // Datastore Mode Status Ring
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isFirebaseAvailable) Color(0xFF132F21) else Color(0xFF332015))
                        .border(
                            1.dp,
                            if (isFirebaseAvailable) Color(0xFF234B33) else Color(0xFF4B3121),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isFirebaseAvailable) Color(0xFF4ADE80) else Color(0xFFFB923C))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isFirebaseAvailable) "Firebase Cloud Mode" else "Local Sandbox Mode",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isFirebaseAvailable) Color(0xFF4ADE80) else Color(0xFFFB923C)
                        )
                    }

                    if (com.example.data.FirebaseService.isFirebaseConfigured()) {
                        TextButton(
                            onClick = {
                                viewModel.toggleFirebaseMode()
                                viewModel.clearAuthMessage()
                            },
                            modifier = Modifier.height(28.dp).testTag("login_toggle_cloud_button")
                        ) {
                            Text(
                                text = if (isFirebaseAvailable) "Use Local" else "Use Cloud",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isFirebaseAvailable) Color(0xFFFB923C) else Color(0xFF4ADE80)
                            )
                        }
                    }
                }

                // Input Fields
                OutlinedTextField(
                    value = usernameOrEmail,
                    onValueChange = { usernameOrEmail = it },
                    label = { Text(if (isFirebaseAvailable) "Email Address" else "Username") },
                    placeholder = { Text(if (isFirebaseAvailable) "e.g. alice@keysync.com" else "e.g. pianist") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GlowCyan,
                        unfocusedBorderColor = DarkGrey,
                        focusedLabelColor = GlowCyan,
                        unfocusedLabelColor = SoftGrey,
                        focusedTextColor = CleanWhite,
                        unfocusedTextColor = CleanWhite
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("login_username_input")
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    placeholder = { Text("Enter your password") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GlowCyan,
                        unfocusedBorderColor = DarkGrey,
                        focusedLabelColor = GlowCyan,
                        unfocusedLabelColor = SoftGrey,
                        focusedTextColor = CleanWhite,
                        unfocusedTextColor = CleanWhite
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .testTag("login_password_input")
                )

                // Info Box / Default Credentials Tips
                Card(
                    colors = CardDefaults.cardColors(containerColor = ThemeDarkBg),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, SoftGrey.copy(alpha = 0.15f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "💡 Login Tip:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlowCyan,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Text(
                            text = if (isFirebaseAvailable) {
                                "If using Cloud mode, configure Firebase credentials in your environment. Alternatively, toggle to 'Local Sandbox Mode' below to play with local test login: username 'pianist' and password 'password'."
                            } else {
                                "For testing, use the pre-loaded account credentials:\nUsername: cyclist\nPassword: cyclist"
                            },
                            fontSize = 10.sp,
                            color = SoftGrey,
                            lineHeight = 14.sp
                        )
                    }
                }

                // Error or Status messages
                authMessage?.let { msg ->
                    Text(
                        text = msg,
                        color = if (authSuccess == true) Color(0xFF4ADE80) else GlowPink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    )
                }

                // Submit/Sign In Button - NO registration/signup
                Button(
                    onClick = {
                        viewModel.login(usernameOrEmail, password)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GlowCyan),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("login_submit_button")
                ) {
                    Text(
                        text = "Sign In",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
