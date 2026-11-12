/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Copyright (C) 2026 AutoClicky contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package nl.tartaruzz.autoclicky.overlay

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.widget.Toast

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbScenario

import nl.tartaruzz.autoclicky.engine.EngineController
import nl.tartaruzz.autoclicky.engine.RepeatSetting
import nl.tartaruzz.autoclicky.engine.toRepeatSetting
import nl.tartaruzz.autoclicky.recording.CompletedRecording
import nl.tartaruzz.autoclicky.recording.DumbScenarioBuilder
import nl.tartaruzz.autoclicky.recording.RecordingController
import nl.tartaruzz.autoclicky.recording.defaultRecordingName

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives the floating [QuickPanelOverlay], the minimal control bar displayed above every other app.
 *
 * It lets the user start a recording, stop a recording or a running template, and play the last template without going
 * back to the AutoClicky activity, which is what makes "perform an action in another app and record it" possible.
 *
 * It also owns the two dialogs the panel can open above the current app:
 * - [SaveRecordingOverlay], shown as soon as a capture ends, so the recording can be saved on the spot,
 * - [ScenarioPickerOverlay], listing the stored templates so one can be chosen and played from anywhere.
 *
 * Visibility rules:
 * - the user can pin the panel from the activity ([setPanelRequested], toggled by [toggle]),
 * - the panel appears on its own while a template is playing, so that it can be stopped from any app,
 * - the panel hides while an overlay is capturing the touches, because the recording overlays already provide their own
 *   "Stop recording" button.
 *
 * The overlays need the running accessibility service, so [attach] is called by it on connection and [detach] on
 * unbinding, mirroring [RecordingController].
 */
@Singleton
class QuickPanelController @Inject constructor(
    private val engineController: EngineController,
    private val recordingController: RecordingController,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var service: AccessibilityService? = null
    private var overlay: QuickPanelOverlay? = null
    private var saveOverlay: SaveRecordingOverlay? = null
    private var pickerOverlay: ScenarioPickerOverlay? = null
    private var repeatOverlay: RepeatOverlay? = null

    private var stateJob: Job? = null
    private var completedRecordingJob: Job? = null
    private var saveRetryJob: Job? = null

    private var isVisibleRequested = false
    private var isDismissedForCurrentRun = false
    private var hasReportedPanelFailure = false
    private var lastState = PanelState()

    /** The recording currently offered in [SaveRecordingOverlay], so it is not displayed twice. */
    private var offeredRecording: CompletedRecording? = null

    /** True while one of the full screen cards is displayed; the panel is hidden during that time. */
    private val isCardOpen: Boolean
        get() = saveOverlay != null || pickerOverlay != null || repeatOverlay != null

    private val _isPanelRequested = MutableStateFlow(false)

    /** True when the user asked, from the activity, for the panel to stay above the other apps. */
    val isPanelRequested: StateFlow<Boolean> = _isPanelRequested

    fun attach(service: AccessibilityService) {
        this.service = service

        stateJob?.cancel()
        stateJob = scope.launch {
            combine(
                engineController.isRunning,
                recordingController.isRecording,
                engineController.scenarios,
                engineController.repeatSetting,
            ) { isRunning, isRecording, scenarios, repeat ->
                PanelState(
                    isRunning = isRunning,
                    isRecording = isRecording,
                    scenarios = scenarios,
                    repeatLabel = repeat.label,
                )
            }.collect { state -> onPanelState(state) }
        }

        completedRecordingJob?.cancel()
        completedRecordingJob = scope.launch {
            recordingController.completedRecording.collect { completed ->
                if (completed == null) {
                    // Saved or discarded: the next recording may be offered again.
                    offeredRecording = null
                } else if (completed !== offeredRecording) {
                    showSaveOverlay(completed)
                }
            }
        }

        applyState()
    }

    fun detach() {
        stateJob?.cancel()
        stateJob = null
        completedRecordingJob?.cancel()
        completedRecordingJob = null
        saveRetryJob?.cancel()
        saveRetryJob = null

        saveOverlay?.dismiss()
        saveOverlay = null
        pickerOverlay?.dismiss()
        pickerOverlay = null
        repeatOverlay?.dismiss()
        repeatOverlay = null
        offeredRecording = null

        overlay?.hide()
        overlay = null
        service = null

        isDismissedForCurrentRun = false
        lastState = PanelState()
    }

    /** Show the panel when it is hidden, hide it when it is displayed. */
    fun toggle() {
        setPanelRequested(!isVisibleRequested)
    }

    /**
     * Called by the accessibility service when the screen is rotated.
     *
     * The panel keeps its absolute position across a rotation, so it can end up outside the screen; and a card that was
     * being attached at that moment may have been refused by the window manager. Both are fixed here: the panel goes
     * back to a visible place, and a recording that is still waiting to be saved is offered again.
     */
    fun onConfigurationChanged() {
        overlay?.onScreenRotated()
        saveOverlay?.onScreenRotated()
        repeatOverlay?.onScreenRotated()
        pickerOverlay?.onScreenRotated()

        val pendingRecording = recordingController.completedRecording.value
        if (pendingRecording != null && saveOverlay == null) {
            showSaveOverlay(pendingRecording)
        }
    }

    /** Pin or unpin the panel to the screen, from the activity. */
    fun setPanelRequested(requested: Boolean) {
        isVisibleRequested = requested
        _isPanelRequested.value = requested
        if (requested) {
            isDismissedForCurrentRun = false
        }

        applyState()
    }

    private fun onPanelState(state: PanelState) {
        // Once the engine stopped, the next run may show the panel again.
        if (!state.isRunning) {
            isDismissedForCurrentRun = false
        }

        lastState = state
        applyState()
    }

    private fun applyState() {
        val state = lastState
        // A card is a full screen window of its own: the panel would sit above it and swallow the taps meant for it.
        val shouldBeVisible = !state.isRecording && !isCardOpen &&
            (isVisibleRequested || (state.isRunning && !isDismissedForCurrentRun))

        if (state.isRecording) {
            // The picker would sit on top of the capture overlay and cannot play anything during a capture.
            pickerOverlay?.dismiss()
            pickerOverlay = null

            repeatOverlay?.dismiss()
            repeatOverlay = null
        }

        if (shouldBeVisible) {
            if (showOverlay()) {
                hasReportedPanelFailure = false
            } else if (!hasReportedPanelFailure) {
                hasReportedPanelFailure = true
                showMessage("Cannot display the floating panel, is the accessibility service enabled?")
            }
        } else {
            overlay?.hide()
        }

        overlay?.setEnabledState(
            canRecord = !state.isRunning && !state.isRecording,
            canStop = state.isRunning || state.isRecording,
            canPlay = state.hasScenarios && !state.isRunning && !state.isRecording,
            canRepeat = state.hasScenarios && !state.isRecording,
            canList = !state.isRecording,
        )
        overlay?.setRepeatLabel(state.repeatLabel)
    }

    private fun showOverlay(): Boolean {
        val service = service ?: return false

        val existingOverlay = overlay
        if (existingOverlay != null) {
            return existingOverlay.show()
        }

        val newOverlay = QuickPanelOverlay(
            service = service,
            onRecordClicked = ::onRecordClicked,
            onRecordGestureClicked = ::onRecordGestureClicked,
            onStopClicked = ::onStopClicked,
            onPlayClicked = ::onPlayClicked,
            onRepeatClicked = ::onRepeatClicked,
            onListClicked = ::onListClicked,
            onHideClicked = ::onHideClicked,
        )
        overlay = newOverlay
        return newOverlay.show()
    }

    private fun onRecordClicked() {
        if (recordingController.isRecording.value) return

        if (engineController.isRunning.value) {
            showMessage("Stop the running template before recording")
            return
        }

        if (!recordingController.startTapCapture()) {
            showMessage("Cannot start the recording, is the accessibility service enabled?")
        } else {
            showMessage("Recording TAPS — use your apps, then press STOP")
        }
    }

    /** Holding the REC button records gestures instead of taps: they are drawn on a dimmed overlay. */
    private fun onRecordGestureClicked() {
        if (recordingController.isRecording.value) return

        if (engineController.isRunning.value) {
            showMessage("Stop the running template before recording")
            return
        }

        if (!recordingController.startGestureCapture()) {
            showMessage("Cannot start the recording, is the accessibility service enabled?")
        } else {
            showMessage("Recording GESTURES — draw them on the screen, then press STOP")
        }
    }

    private fun onStopClicked() {
        if (recordingController.isRecording.value) {
            recordingController.stopCapture()
        } else {
            engineController.stopScenario()
        }
    }

    private fun onPlayClicked() {
        scope.launch {
            if (!engineController.startLastUsedScenario()) {
                showMessage("No template to play yet, record one first")
            }
        }
    }

    /** Offer the repeat choices for the last template, and play it with the chosen one. */
    private fun onRepeatClicked() {
        val service = service ?: return

        dismissRepeatOverlay()

        val lastScenarioId = engineController.lastStartedScenarioId.value
        val template = lastState.scenarios.firstOrNull { scenario -> scenario.id.databaseId == lastScenarioId }
            ?: lastState.scenarios.firstOrNull()
            ?: return showMessage("No template to play yet, record one first")

        val overlay = RepeatOverlay(
            service = service,
            templateName = template.name,
            initialRepeat = template.toRepeatSetting(),
            onPlay = ::startWithRepeat,
            onCancel = ::dismissRepeatOverlay,
        )
        repeatOverlay = overlay

        if (!overlay.show()) {
            repeatOverlay = null
            showMessage("Cannot display the repeat dialog, is the accessibility service enabled?")
        }

        applyState()
    }

    private fun dismissRepeatOverlay() {
        repeatOverlay?.dismiss()
        repeatOverlay = null
        applyState()
    }

    private fun startWithRepeat(repeat: RepeatSetting) {
        dismissRepeatOverlay()

        scope.launch {
            val scenarioId = engineController.lastStartedScenarioId.value
                ?: lastState.scenarios.firstOrNull()?.id?.databaseId
                ?: return@launch showMessage("No template to play yet, record one first")

            try {
                // Stored in the template first: the next PLAY, from anywhere, uses the same repeat.
                engineController.persistScenarioRepeat(scenarioId, repeat)
                engineController.setRepeatSetting(repeat)

                if (!engineController.startScenario(scenarioId)) {
                    showMessage("Cannot play this template, is the accessibility service enabled?")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cannot save the repeat setting of the template", e)
                showMessage("Cannot play this template, the template could not be updated")
            }
        }
    }

    private fun onListClicked() {
        val service = service ?: return

        pickerOverlay?.dismiss()

        val picker = ScenarioPickerOverlay(
            service = service,
            scenarios = lastState.scenarios,
            isScenarioEnabled = !lastState.isRunning && !lastState.isRecording,
            onScenarioSelected = ::onScenarioSelected,
            onDismissed = {
                pickerOverlay = null
                applyState()
            },
        )
        pickerOverlay = picker

        if (!picker.show()) {
            pickerOverlay = null
            showMessage("Cannot display the template list, is the accessibility service enabled?")
        }

        applyState()
    }

    private fun onScenarioSelected(scenario: DumbScenario) {
        pickerOverlay?.dismiss()
        pickerOverlay = null
        applyState()

        scope.launch {
            if (!engineController.startScenario(scenario.id.databaseId)) {
                showMessage("Cannot play this template, is the accessibility service enabled?")
            }
        }
    }

    /** Offer the recording that was just captured, above the app the user is in. */
    private fun showSaveOverlay(recording: CompletedRecording) {
        if (attachSaveOverlay(recording)) return

        showMessage("Cannot display the save dialog now, retrying")

        // Adding a window can be refused while the display is rotating: keep trying for a moment, the recording is
        // still pending (it is only consumed when it is saved).
        saveRetryJob?.cancel()
        saveRetryJob = scope.launch {
            repeat(SAVE_OVERLAY_RETRY_COUNT) {
                delay(SAVE_OVERLAY_RETRY_DELAY_MS)
                if (attachSaveOverlay(recording)) return@launch
            }
        }
    }

    /**
     * Build and attach the save card.
     *
     * @return false when the card is not displayed, for instance because the window manager refused the window during a
     * rotation.
     */
    private fun attachSaveOverlay(recording: CompletedRecording): Boolean {
        if (saveOverlay != null) return true

        val service = service ?: return false

        offeredRecording = recording

        val overlay = SaveRecordingOverlay(
            service = service,
            summary = recording.summary,
            recordingName = defaultRecordingName(),
            onSave = { saveRecording(recording) },
            onCancel = {
                saveOverlay?.dismiss()
                saveOverlay = null
                applyState()
            },
        )
        saveOverlay = overlay

        if (!overlay.show()) {
            saveOverlay = null
            return false
        }

        applyState()
        return true
    }

    private fun saveRecording(recording: CompletedRecording) {
        saveOverlay?.dismiss()
        saveOverlay = null
        applyState()

        val templateName = defaultRecordingName()

        scope.launch {
            // A new template plays once; the repeat is chosen with the ×N button when it is played.
            val repeat = RepeatSetting.SINGLE
            engineController.setRepeatSetting(repeat)

            val scenarioId = try {
                engineController.addScenario(DumbScenarioBuilder.build(templateName, recording.actions, repeat))
            } catch (e: Exception) {
                Log.e(TAG, "Cannot save the recording", e)
                null
            }
            recordingController.consumeCompletedRecording()

            if (scenarioId == null) {
                showMessage("Could not save the recording")
            } else {
                showMessage("Saved: $templateName (${recording.summary})")
            }
        }
    }

    private fun onHideClicked() {
        isVisibleRequested = false
        _isPanelRequested.value = false

        if (lastState.isRunning) {
            isDismissedForCurrentRun = true
        }

        applyState()
    }

    private fun showMessage(message: String) {
        val service = service ?: return
        Toast.makeText(service, message, Toast.LENGTH_SHORT).show()
    }

    /** Everything the panel needs to know to draw itself. */
    private data class PanelState(
        val isRunning: Boolean = false,
        val isRecording: Boolean = false,
        val scenarios: List<DumbScenario> = emptyList(),
        val repeatLabel: String = RepeatSetting.SINGLE.label,
    ) {
        val hasScenarios: Boolean get() = scenarios.isNotEmpty()
    }

    private companion object {
        const val TAG = "QuickPanelController"

        /** How often the save card is retried when the window manager refuses it, and how long between two tries. */
        const val SAVE_OVERLAY_RETRY_COUNT = 6
        const val SAVE_OVERLAY_RETRY_DELAY_MS = 300L
    }
}
