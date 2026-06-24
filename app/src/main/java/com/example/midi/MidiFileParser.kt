package com.example.midi

import android.util.Log
import com.example.api.AnalyzedNote
import com.example.api.AnalyzedSongResult
import java.io.BufferedInputStream
import java.io.InputStream

object MidiFileParser {
    private const val TAG = "MidiFileParser"

    fun parseMidi(inputStream: InputStream, fileName: String): AnalyzedSongResult? {
        val bis = BufferedInputStream(inputStream)
        try {
            // Read "MThd" header chunk
            val headerId = readString(bis, 4)
            if (headerId != "MThd") {
                Log.e(TAG, "Not a valid MIDI file. Header ID: $headerId")
                return null
            }

            val headerLength = readInt32(bis)
            if (headerLength < 6) {
                Log.e(TAG, "Invalid header length: $headerLength")
                return null
            }

            val format = readInt16(bis)
            val numTracks = readInt16(bis)
            val division = readInt16(bis)

            // Skip remaining bytes if headerLength > 6
            if (headerLength > 6) {
                bis.skip((headerLength - 6).toLong())
            }

            val allNotes = mutableListOf<ParsedMidiNote>()
            val lyrics = mutableListOf<ParsedMidiText>()

            // Read all tracks
            for (trackIdx in 0 until numTracks) {
                val trackId = readString(bis, 4)
                if (trackId != "MTrk") {
                    Log.w(TAG, "Expected MTrk but found: $trackId. Skipping...")
                    try {
                        val chunkLen = readInt32(bis)
                        bis.skip(chunkLen.toLong())
                    } catch (e: Exception) {
                        break
                    }
                    continue
                }

                val trackLength = readInt32(bis)
                val trackData = ByteArray(trackLength)
                var bytesRead = 0
                while (bytesRead < trackLength) {
                    val read = bis.read(trackData, bytesRead, trackLength - bytesRead)
                    if (read == -1) break
                    bytesRead += read
                }

                // Parse track data
                parseTrackData(trackData, allNotes, lyrics)
            }

            if (allNotes.isEmpty()) {
                Log.e(TAG, "No notes found in MIDI file.")
                return null
            }

            // Sort notes by absolute ticks / time
            allNotes.sortBy { it.absoluteTick }

            // Align lyrics with notes if lyrics are present
            val finalNotes = mutableListOf<AnalyzedNote>()
            if (lyrics.isNotEmpty()) {
                lyrics.sortBy { it.absoluteTick }
                for (i in allNotes.indices) {
                    val note = allNotes[i]
                    val pitch = midiNoteToPitchName(note.midiNumber)
                    // Find closest lyric text
                    val matchingLyric = lyrics.minByOrNull { Math.abs(it.absoluteTick - note.absoluteTick) }
                    val lyricText = if (matchingLyric != null && Math.abs(matchingLyric.absoluteTick - note.absoluteTick) < 1000) {
                        matchingLyric.text.trim().removePrefix("-").removeSuffix("-")
                    } else {
                        pitch
                    }
                    finalNotes.add(AnalyzedNote(pitch = pitch, lyric = if (lyricText.isEmpty() || !lyricText.any { it.isLetterOrDigit() }) pitch else lyricText))
                }
            } else {
                for (note in allNotes) {
                    val pitch = midiNoteToPitchName(note.midiNumber)
                    finalNotes.add(AnalyzedNote(pitch = pitch, lyric = pitch))
                }
            }

            // Extract title from filename
            val cleanTitle = fileName.substringBeforeLast(".")
                .replace("_", " ")
                .replace("-", " ")
                .split(" ")
                .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }

            return AnalyzedSongResult(
                title = cleanTitle,
                artist = "MIDI Import",
                difficulty = when {
                    allNotes.size < 15 -> "Easy"
                    allNotes.size < 40 -> "Medium"
                    else -> "Hard"
                },
                description = "Imported from MIDI file: $fileName. Dynamic practice track with ${allNotes.size} notes.",
                notes = finalNotes
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing MIDI: ${e.message}", e)
            return null
        }
    }

    private fun parseTrackData(
        data: ByteArray,
        notes: MutableList<ParsedMidiNote>,
        lyrics: MutableList<ParsedMidiText>
    ) {
        var offset = 0
        var runningStatus = 0
        var absoluteTick = 0L

        while (offset < data.size) {
            // Read delta-time
            val deltaResult = readVlqFromBytes(data, offset)
            val deltaTime = deltaResult.value
            offset = deltaResult.nextOffset
            absoluteTick += deltaTime

            if (offset >= data.size) break

            var status = data[offset].toInt() and 0xFF
            if (status >= 0x80) {
                runningStatus = status
                offset++
            } else {
                status = runningStatus
            }

            val cmd = status and 0xF0
            when {
                status == 0xFF -> {
                    // Meta Event
                    if (offset + 1 >= data.size) break
                    val metaType = data[offset].toInt() and 0xFF
                    offset++
                    val lenResult = readVlqFromBytes(data, offset)
                    val len = lenResult.value
                    offset = lenResult.nextOffset

                    if (offset + len > data.size) break

                    if (metaType == 0x05 || metaType == 0x01) { // Lyric (0x05) or Text (0x01)
                        val text = String(data, offset, len, Charsets.UTF_8).trim()
                        if (text.isNotEmpty()) {
                            lyrics.add(ParsedMidiText(absoluteTick, text))
                        }
                    }
                    offset += len
                }
                status == 0xF0 || status == 0xF7 -> {
                    // Sysex Event
                    val lenResult = readVlqFromBytes(data, offset)
                    val len = lenResult.value
                    offset = lenResult.nextOffset
                    offset += len
                }
                cmd == 0x90 -> { // Note On
                    if (offset + 1 >= data.size) break
                    val note = data[offset].toInt() and 0xFF
                    val vel = data[offset + 1].toInt() and 0xFF
                    offset += 2
                    if (vel > 0) {
                        notes.add(ParsedMidiNote(absoluteTick, note))
                    }
                }
                cmd == 0x80 -> { // Note Off
                    offset += 2
                }
                cmd == 0xA0 || cmd == 0xB0 || cmd == 0xE0 -> {
                    offset += 2
                }
                cmd == 0xC0 || cmd == 0xD0 -> {
                    offset += 1
                }
                else -> {
                    // Unknown status or unhandled. Just break to prevent infinite loops
                    break
                }
            }
        }
    }

    private data class ParsedMidiNote(val absoluteTick: Long, val midiNumber: Int)
    private data class ParsedMidiText(val absoluteTick: Long, val text: String)

    private fun readString(bis: InputStream, len: Int): String {
        val bytes = ByteArray(len)
        bis.read(bytes)
        return String(bytes, Charsets.US_ASCII)
    }

    private fun readInt32(bis: InputStream): Int {
        val b1 = bis.read()
        val b2 = bis.read()
        val b3 = bis.read()
        val b4 = bis.read()
        return (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4
    }

    private fun readInt16(bis: InputStream): Int {
        val b1 = bis.read()
        val b2 = bis.read()
        return (b1 shl 8) or b2
    }

    private data class VlqResult(val value: Int, val nextOffset: Int)

    private fun readVlqFromBytes(data: ByteArray, startOffset: Int): VlqResult {
        var value = 0
        var offset = startOffset
        do {
            val byte = data[offset].toInt() and 0xFF
            offset++
            value = (value shl 7) or (byte and 0x7F)
        } while ((byte and 0x80) != 0 && offset < data.size)
        return VlqResult(value, offset)
    }

    private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    private fun midiNoteToPitchName(midiNumber: Int): String {
        val noteIndex = midiNumber % 12
        val octave = (midiNumber / 12) - 1
        return "${NOTE_NAMES[noteIndex]}$octave"
    }
}
