package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sqrt

class AudioAnalyser {
    private val _isListening = MutableStateFlow(false)
    val isListening = _isListening.asStateFlow()

    private val _detectedFrequency = MutableStateFlow(0f)
    val detectedFrequency = _detectedFrequency.asStateFlow()

    private val _detectedNote = MutableStateFlow<String?>(null)
    val detectedNote = _detectedNote.asStateFlow()

    private val _detectedNotes = MutableStateFlow<List<String>>(emptyList())
    val detectedNotes = _detectedNotes.asStateFlow()

    private val _detectedChord = MutableStateFlow<String?>(null)
    val detectedChord = _detectedChord.asStateFlow()

    private val _waveform = MutableStateFlow(FloatArray(30) { 0f })
    val waveform = _waveform.asStateFlow()

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val sampleRate = 22050 // Lower sample rate means better frequency resolution in fewer samples!
    private val bufferSize = 2048   // 2048 samples at 22050Hz is ~93ms, perfect for pitch detection.

    private var isRecording = false
    private val recordedBytes = java.io.ByteArrayOutputStream()

    fun startRecording() {
        synchronized(recordedBytes) {
            recordedBytes.reset()
            isRecording = true
        }
    }

    fun stopRecording(): ByteArray? {
        synchronized(recordedBytes) {
            isRecording = false
            val rawBytes = recordedBytes.toByteArray()
            recordedBytes.reset()
            if (rawBytes.isEmpty()) return null

            val totalAudioLen = rawBytes.size.toLong()
            val totalDataLen = totalAudioLen + 36
            val header = writeWavHeader(
                totalAudioLen = totalAudioLen,
                totalDataLen = totalDataLen,
                longSampleRate = sampleRate.toLong(),
                channels = 1,
                byteRate = sampleRate.toLong() * 2
            )

            val wavBytes = ByteArray(44 + rawBytes.size)
            System.arraycopy(header, 0, wavBytes, 0, 44)
            System.arraycopy(rawBytes, 0, wavBytes, 44, rawBytes.size)
            return wavBytes
        }
    }

    private fun writeWavHeader(
        totalAudioLen: Long,
        totalDataLen: Long,
        longSampleRate: Long,
        channels: Int,
        byteRate: Long
    ): ByteArray {
        val header = ByteArray(44)
        header[0] = 'R'.code.toByte() // RIFF
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = ((totalDataLen shr 8) and 0xff).toByte()
        header[6] = ((totalDataLen shr 16) and 0xff).toByte()
        header[7] = ((totalDataLen shr 24) and 0xff).toByte()
        header[8] = 'W'.code.toByte() // WAVE
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte() // 'fmt ' chunk
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        header[16] = 16 // size of 'fmt ' chunk
        header[17] = 0
        header[18] = 0
        header[19] = 0
        header[20] = 1 // format = 1 (PCM)
        header[21] = 0
        header[22] = channels.toByte()
        header[23] = 0
        header[24] = (longSampleRate and 0xff).toByte()
        header[25] = ((longSampleRate shr 8) and 0xff).toByte()
        header[26] = ((longSampleRate shr 16) and 0xff).toByte()
        header[27] = ((longSampleRate shr 24) and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = ((byteRate shr 8) and 0xff).toByte()
        header[30] = ((byteRate shr 16) and 0xff).toByte()
        header[31] = ((byteRate shr 24) and 0xff).toByte()
        header[32] = (channels * 2).toByte()
        header[33] = 0
        header[34] = 16 // bits per sample
        header[35] = 0
        header[36] = 'd'.code.toByte() // 'data' chunk
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        header[40] = (totalAudioLen and 0xff).toByte()
        header[41] = ((totalAudioLen shr 8) and 0xff).toByte()
        header[42] = ((totalAudioLen shr 16) and 0xff).toByte()
        header[43] = ((totalAudioLen shr 24) and 0xff).toByte()
        return header
    }

    @SuppressLint("MissingPermission")
    fun startListening(context: android.content.Context, scope: CoroutineScope) {
        if (_isListening.value) return

        recordingJob = scope.launch(Dispatchers.IO) {
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            val finalBufferSize = maxOf(minBuf, bufferSize)

            try {
                val audioContext = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    context.createAttributionContext("audio")
                } else {
                    context
                }

                audioRecord = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    AudioRecord.Builder()
                        .setContext(audioContext)
                        .setAudioSource(MediaRecorder.AudioSource.MIC)
                        .setAudioFormat(
                            AudioFormat.Builder()
                                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sampleRate)
                                .build()
                        )
                        .setBufferSizeInBytes(finalBufferSize)
                        .build()
                } else {
                    AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        finalBufferSize
                    )
                }

                if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                    Log.e("AudioAnalyser", "AudioRecord failed to initialize")
                    return@launch
                }

                audioRecord?.startRecording()
                _isListening.value = true

                val audioBuffer = ShortArray(bufferSize)

                while (isActive && _isListening.value) {
                    val readResult = audioRecord?.read(audioBuffer, 0, bufferSize) ?: 0
                    if (readResult > 0) {
                        if (isRecording) {
                            synchronized(recordedBytes) {
                                for (i in 0 until readResult) {
                                    val value = audioBuffer[i]
                                    recordedBytes.write(value.toInt() and 0xFF)
                                    recordedBytes.write((value.toInt() shr 8) and 0xFF)
                                }
                            }
                        }
                        // 1. Calculate RMS to filter background noise & update waveform for dynamic visualizer
                        val rms = calculateRMS(audioBuffer, readResult)
                        
                        // Populate wave state for animated ui
                        val subsampled = FloatArray(30)
                        val step = (readResult / 30).coerceAtLeast(1)
                        for (i in 0 until 30) {
                            val index = i * step
                            if (index < readResult) {
                                // Scale value for presentation
                                val amp = Math.abs(audioBuffer[index].toFloat()) / 32768f
                                subsampled[i] = amp * (if (rms > 0.01f) 1.5f else 0.1f)
                            }
                        }
                        _waveform.value = subsampled

                        if (rms > 0.012f) { // Threshold for actual note input
                            // 2. Compute pitch and detect multiple simultaneous notes
                            val detectedPitches = detectMultiplePitches(audioBuffer, readResult, sampleRate.toFloat())
                            if (detectedPitches.isNotEmpty()) {
                                val topFreq = detectedPitches.first().second
                                val topNote = detectedPitches.first().first
                                
                                _detectedFrequency.value = topFreq
                                _detectedNote.value = topNote
                                
                                val notes = detectedPitches.map { it.first }
                                _detectedNotes.value = notes
                                
                                val chord = identifyChord(notes.toSet())
                                _detectedChord.value = chord
                            } else {
                                _detectedFrequency.value = 0f
                                _detectedNote.value = null
                                _detectedNotes.value = emptyList()
                                _detectedChord.value = null
                            }
                        } else {
                            _detectedFrequency.value = 0f
                            _detectedNote.value = null
                            _detectedNotes.value = emptyList()
                            _detectedChord.value = null
                        }
                    }
                    delay(60) // refresh rate ~15 FPS, perfect for both responsiveness and CPU efficiency
                }
            } catch (e: Exception) {
                Log.e("AudioAnalyser", "Audio recording error", e)
            } finally {
                stopAndRelease()
            }
        }
    }

    fun stopListening() {
        _isListening.value = false
        recordingJob?.cancel()
        recordingJob = null
        stopAndRelease()
        _detectedFrequency.value = 0f
        _detectedNote.value = null
        _detectedNotes.value = emptyList()
        _detectedChord.value = null
        _waveform.value = FloatArray(30) { 0f }
    }

    private fun stopAndRelease() {
        try {
            audioRecord?.let {
                if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e("AudioAnalyser", "Error releasing AudioRecord", e)
        }
        audioRecord = null
    }

    private fun calculateRMS(buffer: ShortArray, length: Int): Float {
        var sum = 0.0
        for (i in 0 until length) {
            sum += buffer[i] * buffer[i]
        }
        return sqrt(sum / length).toFloat() / 32768f
    }

    // Autocorrelation pitch detection
    private fun detectPitchYin(buffer: ShortArray, length: Int, sampleRate: Float): Float {
        // Calculate autocorrelation for potential pitch periods (lags)
        // Frequency range 130Hz (C3) to 1000Hz (C6) corresponding to lags:
        // lagMax = sampleRate / 130
        // lagMin = sampleRate / 1000
        val lagMin = (sampleRate / 1000f).roundToInt()
        val lagMax = (sampleRate / 130f).roundToInt().coerceAtMost(length / 2 - 1)

        val r = FloatArray(lagMax + 1)
        for (lag in lagMin..lagMax) {
            var sum = 0f
            for (i in 0 until length / 2) {
                sum += buffer[i].toFloat() * buffer[i + lag].toFloat()
            }
            r[lag] = sum
        }

        // Find primary peak
        var bestCorrelation = 0f
        var bestLag = -1
        for (lag in lagMin..lagMax) {
            if (r[lag] > bestCorrelation) {
                // Peak detection (must be higher than neighboring points)
                if (lag > lagMin && lag < lagMax && r[lag] > r[lag - 1] && r[lag] > r[lag + 1]) {
                    bestCorrelation = r[lag]
                    bestLag = lag
                }
            }
        }

        if (bestLag != -1) {
            return sampleRate / bestLag.toFloat()
        }
        return -1f
    }

    private fun frequencyToNoteName(frequency: Float): String? {
        if (frequency <= 0f) return null
        val n = 12 * log2(frequency.toDouble() / 440.0) + 69.0
        val noteNumber = n.roundToInt()

        val noteNames = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        val octave = (noteNumber / 12) - 1
        val noteIndex = (noteNumber % 12 + 12) % 12
        if (octave in 3..6) { // Focus on middle-to-high octaves (piano C3 - C6)
            return "${noteNames[noteIndex]}$octave"
        }
        return null
    }

    private fun frequencyToMidiNumber(frequency: Float): Int? {
        if (frequency <= 0f) return null
        val n = 12 * log2(frequency.toDouble() / 440.0) + 69.0
        return n.roundToInt()
    }

    fun detectMultiplePitches(buffer: ShortArray, length: Int, sampleRate: Float): List<Pair<String, Float>> {
        val lagMin = (sampleRate / 1100f).roundToInt() // Up to C6 (1100 Hz)
        val lagMax = (sampleRate / 130f).roundToInt().coerceAtMost(length / 2 - 1) // Down to C3 (130 Hz)

        val r = FloatArray(lagMax + 1)
        for (lag in lagMin..lagMax) {
            var sum = 0f
            for (i in 0 until length / 2) {
                sum += buffer[i].toFloat() * buffer[i + lag].toFloat()
            }
            r[lag] = sum
        }

        val peaks = mutableListOf<Pair<Int, Float>>()
        val maxVal = r.maxOrNull() ?: 1f
        if (maxVal > 0f) {
            val minPeakThreshold = maxVal * 0.35f // Threshold to identify secondary notes
            for (lag in lagMin..lagMax) {
                if (r[lag] > minPeakThreshold) {
                    if (lag > lagMin && lag < lagMax && r[lag] > r[lag - 1] && r[lag] > r[lag + 1]) {
                        peaks.add(Pair(lag, r[lag]))
                    }
                }
            }
        }

        val detectedList = mutableListOf<Pair<String, Float>>()
        val midiNotesList = mutableSetOf<Int>()

        // Sort descending by autocorrelation value to keep strongest dominant harmonics/frequencies first
        for (peak in peaks.sortedByDescending { it.second }) {
            val lag = peak.first
            val freq = sampleRate / lag.toFloat()
            if (freq in 130f..1100f) {
                val noteName = frequencyToNoteName(freq)
                val midiNote = frequencyToMidiNumber(freq)
                if (noteName != null && midiNote != null) {
                    var isDuplicate = false
                    for (existingMidi in midiNotesList) {
                        val absDiff = Math.abs(existingMidi - midiNote)
                        // Group close semitones and identical classes at different octaves together to avoid ghosting
                        if (absDiff == 0 || absDiff % 12 == 0 || absDiff <= 1) {
                            isDuplicate = true
                            break
                        }
                    }
                    if (!isDuplicate) {
                        midiNotesList.add(midiNote)
                        detectedList.add(Pair(noteName, freq))
                    }
                }
            }
        }
        return detectedList
    }

    fun identifyChord(notes: Set<String>): String? {
        if (notes.size < 2) return null
        val pitchClasses = notes.map { it.replace(Regex("\\d"), "") }.toSet()

        val roots = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

        for (root in roots) {
            val rootIdx = roots.indexOf(root)
            val majorThird = roots[(rootIdx + 4) % 12]
            val minorThird = roots[(rootIdx + 3) % 12]
            val fifth = roots[(rootIdx + 7) % 12]

            // Major Triad
            if (pitchClasses.contains(root) && pitchClasses.contains(majorThird) && pitchClasses.contains(fifth)) {
                return "$root Major"
            }
            // Minor Triad
            if (pitchClasses.contains(root) && pitchClasses.contains(minorThird) && pitchClasses.contains(fifth)) {
                return "$root Minor"
            }
        }

        // Fallback checks for simple partial dyads
        for (root in roots) {
            val rootIdx = roots.indexOf(root)
            val majorThird = roots[(rootIdx + 4) % 12]
            val minorThird = roots[(rootIdx + 3) % 12]
            val fifth = roots[(rootIdx + 7) % 12]

            if (pitchClasses.contains(root) && pitchClasses.contains(majorThird)) {
                return "$root Major (no 5th)"
            }
            if (pitchClasses.contains(root) && pitchClasses.contains(minorThird)) {
                return "$root Minor (no 5th)"
            }
            if (pitchClasses.contains(root) && pitchClasses.contains(fifth)) {
                return "$root Power Chord"
            }
        }
        return null
    }
}
