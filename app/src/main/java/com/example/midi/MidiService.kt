package com.example.midi

import android.content.Context
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow

@Suppress("DEPRECATION")
object MidiService {
    private const val TAG = "MidiService"

    private var midiManager: MidiManager? = null
    private var openMidiDevice: MidiDevice? = null
    private var midiOutputPort: MidiOutputPort? = null

    private val _isSupported = MutableStateFlow(false)
    val isSupported = _isSupported.asStateFlow()

    private val _connectedDeviceName = MutableStateFlow<String?>(null)
    val connectedDeviceName = _connectedDeviceName.asStateFlow()

    private val _availableDevices = MutableStateFlow<List<MidiDeviceInfo>>(emptyList())
    val availableDevices = _availableDevices.asStateFlow()

    // Map of active midi note numbers currently pressed down on the physical MIDI keyboard
    private val _activeMidiNotes = MutableStateFlow<Set<Int>>(emptySet())
    val activeMidiNotes = _activeMidiNotes.asStateFlow()

    // List of active note name strings pressed down (e.g. "C4", "E4")
    private val _activeNoteNames = MutableStateFlow<List<String>>(emptyList())
    val activeNoteNames = _activeNoteNames.asStateFlow()

    // High precision event flow of newly pressed MIDI keys (for instant trigger)
    private val _midiNoteOnEvents = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 64)
    val midiNoteOnEvents = _midiNoteOnEvents.asSharedFlow()

    private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    private val handler = Handler(Looper.getMainLooper())

    fun init(context: Context) {
        val hasFeature = context.packageManager.hasSystemFeature(Context.MIDI_SERVICE) ||
                context.packageManager.hasSystemFeature("android.software.midi")
        
        if (!hasFeature) {
            Log.w(TAG, "MIDI is not supported on this device.")
            _isSupported.value = false
            return
        }

        try {
            val manager = context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
            if (manager == null) {
                _isSupported.value = false
                return
            }
            midiManager = manager
            _isSupported.value = true
            Log.d(TAG, "MidiService successfully initialized.")

            // Refresh initial list of devices
            refreshDevices()

            // Listen for device register/unregister updates
            manager.registerDeviceCallback(object : MidiManager.DeviceCallback() {
                override fun onDeviceAdded(device: MidiDeviceInfo) {
                    Log.d(TAG, "MIDI Device Added: ${device.properties.getString(MidiDeviceInfo.PROPERTY_NAME)}")
                    refreshDevices()
                    autoConnectDevice(device)
                }

                override fun onDeviceRemoved(device: MidiDeviceInfo) {
                    Log.d(TAG, "MIDI Device Removed: ${device.properties.getString(MidiDeviceInfo.PROPERTY_NAME)}")
                    refreshDevices()
                    if (openMidiDevice?.info?.id == device.id) {
                        disconnectCurrentDevice()
                    }
                }
            }, handler)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize MidiManager: ${e.message}", e)
            _isSupported.value = false
        }
    }

    fun refreshDevices() {
        val manager = midiManager ?: return
        try {
            _availableDevices.value = manager.devices.toList()
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "Error listing MIDI devices: ${e.message}")
        }
    }

    private fun autoConnectDevice(device: MidiDeviceInfo) {
        // Auto connect if no device is open and it has output ports (ports where the device sends outputs to Android input)
        if (openMidiDevice == null && device.outputPortCount > 0) {
            connectDevice(device)
        }
    }

    fun connectDevice(deviceInfo: MidiDeviceInfo) {
        val manager = midiManager ?: return
        disconnectCurrentDevice()

        val name = deviceInfo.properties.getString(MidiDeviceInfo.PROPERTY_NAME) ?: "Controller Keyboard"
        Log.d(TAG, "Connecting to MIDI Device: $name")

        try {
            manager.openDevice(deviceInfo, { device ->
                if (device == null) {
                    Log.e(TAG, "Failed to open MIDI Device: $name")
                    return@openDevice
                }
                openMidiDevice = device
                _connectedDeviceName.value = name

                // Connect to output port 0 of the MIDI keyboard
                val outputPort = device.openOutputPort(0)
                if (outputPort == null) {
                    Log.e(TAG, "Failed to open MIDI output port on $name")
                    disconnectCurrentDevice()
                    return@openDevice
                }

                midiOutputPort = outputPort
                outputPort.connect(MidiReceiverImpl())
                Log.d(TAG, "Successfully connected output port to receiver for device: $name")
            }, handler)
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "Error opening MIDI device: ${e.message}", e)
        }
    }

    fun disconnectCurrentDevice() {
        try {
            midiOutputPort?.close()
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "Error closing midi output port: ${e.message}")
        }
        midiOutputPort = null

        try {
            openMidiDevice?.close()
        } catch (e: java.lang.Exception) {
            Log.e(TAG, "Error closing midi device: ${e.message}")
        }
        openMidiDevice = null
        _connectedDeviceName.value = null
        _activeMidiNotes.value = emptySet()
        _activeNoteNames.value = emptyList()
        Log.d(TAG, "Disconnected from current MIDI device.")
    }

    fun midiNoteToName(midiNumber: Int): String {
        val noteIndex = midiNumber % 12
        val octave = (midiNumber / 12) - 1
        return "${NOTE_NAMES[noteIndex]}$octave"
    }

    private class MidiReceiverImpl : MidiReceiver() {
        private var runningStatus = 0
        private val pendingBytes = ByteArray(2)
        private var pendingCount = 0

        private fun getDataBytesCount(status: Int): Int {
            return when (status and 0xF0) {
                0x80, 0x90, 0xA0, 0xB0, 0xE0 -> 2
                0xC0, 0xD0 -> 1
                else -> 0
            }
        }

        override fun onSend(data: ByteArray?, offset: Int, count: Int, timestamp: Long) {
            if (data == null || count <= 0) return

            for (i in offset until (offset + count)) {
                val byteVal = data[i].toInt() and 0xFF

                if (byteVal >= 0xF8) {
                    // System Real-Time Message
                    continue
                }

                if (byteVal >= 0x80) {
                    // Status byte
                    if (byteVal < 0xF0) {
                        runningStatus = byteVal
                        pendingCount = 0
                    } else {
                        // System Common
                        runningStatus = 0
                        pendingCount = 0
                    }
                } else {
                    // Data byte
                    if (runningStatus != 0) {
                        val targetCount = getDataBytesCount(runningStatus)
                        if (targetCount > 0) {
                            if (pendingCount < targetCount) {
                                pendingBytes[pendingCount] = data[i]
                                pendingCount++
                            }
                            if (pendingCount == targetCount) {
                                handleMidiMessage(runningStatus, pendingBytes[0].toInt() and 0xFF, if (targetCount > 1) pendingBytes[1].toInt() and 0xFF else 0)
                                pendingCount = 0
                            }
                        }
                    }
                }
            }
        }

        private fun handleMidiMessage(status: Int, data1: Int, data2: Int) {
            val cmd = status and 0xF0
            if (cmd == 0x90) { // Note On
                if (data2 > 0) {
                    handleNoteOn(data1)
                } else {
                    handleNoteOff(data1)
                }
            } else if (cmd == 0x80) { // Note Off
                handleNoteOff(data1)
            }
        }
    }

    private fun handleNoteOn(noteNumber: Int) {
        val currentNotes = _activeMidiNotes.value.toMutableSet()
        currentNotes.add(noteNumber)
        _activeMidiNotes.value = currentNotes

        val noteName = midiNoteToName(noteNumber)
        val namesList = currentNotes.map { midiNoteToName(it) }
        _activeNoteNames.value = namesList
        _midiNoteOnEvents.tryEmit(noteName)
        Log.d(TAG, "MIDI Key Pressed: $noteNumber ($noteName)")
    }

    private fun handleNoteOff(noteNumber: Int) {
        val currentNotes = _activeMidiNotes.value.toMutableSet()
        currentNotes.remove(noteNumber)
        _activeMidiNotes.value = currentNotes

        val namesList = currentNotes.map { midiNoteToName(it) }
        _activeNoteNames.value = namesList
        Log.d(TAG, "MIDI Key Released: $noteNumber")
    }
}
