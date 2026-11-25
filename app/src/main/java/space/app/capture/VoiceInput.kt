package space.app.capture

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import space.app.voice.VoiceNotes

sealed interface VoiceState {
    data object Idle : VoiceState
    data object Listening : VoiceState
    data object Transcribing : VoiceState
    /** [audio] is kept with the item it becomes, so a wrong transcript never loses what you said. */
    class Heard(val text: String, val audio: FloatArray) : VoiceState
    data class Problem(val message: String) : VoiceState
}

/** The mic button's state, owned by one ViewModel ([scope] is its viewModelScope). */
class VoiceInput(private val scope: CoroutineScope, private val recorder: Recorder, private val notes: VoiceNotes) {
    private val _state = MutableStateFlow<VoiceState>(VoiceState.Idle)
    val state: StateFlow<VoiceState> = _state.asStateFlow()
    private var recording: Recording? = null

    /** The last recording, until the capture it belongs to is saved. */
    var audio: FloatArray? = null
        private set

    fun toggle() {
        val current = recording
        if (current == null) start() else stop(current)
    }

    private fun start() {
        if (recorder.inCall()) { _state.value = VoiceState.Problem("Can't record during a call"); return }
        recording = recorder.start()
        _state.value = VoiceState.Listening
    }

    private fun stop(r: Recording) {
        recording = null
        _state.value = VoiceState.Transcribing
        scope.launch {
            val samples = withContext(Dispatchers.IO) { r.stop() }
            val text = notes.transcribe(samples)
            _state.value = if (text == null) VoiceState.Problem("Didn't hear anything") else VoiceState.Heard(text, samples)
            if (text != null) audio = samples
        }
    }

    /** Hands over the recording to save with the capture, once. */
    fun takeAudio(): FloatArray? = audio.also { audio = null }

    fun reset() {
        recording?.stop()
        recording = null
        audio = null
        _state.value = VoiceState.Idle
    }
}
