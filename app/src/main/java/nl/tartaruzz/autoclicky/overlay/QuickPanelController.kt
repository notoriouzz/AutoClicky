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
 * It lets the user start a recording, save it with the ✕ button, and play the last template, all without going back to
 * the AutoClicky activity, which is what makes "perform an action in another app and record it" possible.
 *
 * It also owns the dialogs the panel can open above the current app: the scenario picker and the repeat choice. A capture
 * is saved automatically when it ends, so no save dialog is needed anymore.
 *
 * Visibility rules:
 * - the user can pin the panel from the activity ([setPanelRequested], toggled by [toggle]),
 * - the panel hides while a template is playing: only the green frame and the play glyph are shown then,
 * - the panel hides while an overlay is capturing the touches, because the recording overlays already provide their own
 *   ✕ button.
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
    private var pickerOverlay: ScenarioPickerOverlay? = null
    private var repeatOverlay: RepeatOverlay? = null

    private var stateJob: Job? = null
    private var completedRecordingJob: Job? = null
    private var emptyNoticeJob: Job? = null
    private var relayNoticeJob: Job? = null

    private var isVisibleRequested = false
    private var hasReportedPanelFailure = false
    private var lastState = PanelState()

    /** True while one of the full screen cards is displayed; the panel is hidden during that time. */
    private val isCardOpen: Boolean
        get() = pickerOverlay != null || repeatOverlay != null

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
                if (completed != null) saveRecording(completed)
            }
        }

        emptyNoticeJob?.cancel()
        emptyNoticeJob = scope.launch {
            recordingController.recordingEmptyNotice.collect { isEmpty ->
                if (isEmpty) {
                    recordingController.acknowledgeEmptyRecording()
                    showMessage("No taps recorded")
                }
            }
        }

        relayNoticeJob?.cancel()
        relayNoticeJob = scope.launch {
            recordingController.isForwardingTouches.collect { isForwarding ->
                if (isForwarding) {
                    // From now on every touch is received by AutoClicky and sent back to the app: the user must know,
                    // because the app now reacts when they release instead of while they press.
                    showMessage("$FORWARDED_CAPTURE_NOTICE")
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
        relayNoticeJob?.cancel()
        relayNoticeJob = null
        emptyNoticeJob?.cancel()
        emptyNoticeJob = null

        pickerOverlay?.dismiss()
        pickerOverlay = null
        repeatOverlay?.dismiss()
        repeatOverlay = null

        overlay?.hide()
        overlay = null
        service = null

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
        repeatOverlay?.onScreenRotated()
        pickerOverlay?.onScreenRotated()
    }

    /** Pin or unpin the panel to the screen, from the activity. */
    fun setPanelRequested(requested: Boolean) {
        isVisibleRequested = requested
        _isPanelRequested.value = requested
        applyState()
    }

    private fun onPanelState(state: PanelState) {
        lastState = state
        applyState()
    }

    private fun applyState() {
        val state = lastState
        // The panel is hidden while the engine plays: only the green frame and the play glyph mark the playback.
        val shouldBeVisible = !state.isRecording && !state.isRunning && !isCardOpen && isVisibleRequested

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
            showMessage("Recording TAPS — use your apps, then tap ✕ to finish and save")
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
            showMessage("Recording GESTURES — draw them on the screen, then tap ✕ to finish and save")
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

    /** Persist the recording that just ended, and make it the one PLAY starts. */
    private fun saveRecording(recording: CompletedRecording) {
        val templateName = defaultRecordingName()

        scope.launch {
            // A new recording plays once; the repeat is chosen with the ×N button when it is played.
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
                showMessage("Recording saved")
            }
        }
    }

    private fun onHideClicked() {
        isVisibleRequested = false
        _isPanelRequested.value = false
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

        /**
         * Shown when the capture had to switch to the relay overlay: the app does not tell where its views are, so the
         * taps are received by AutoClicky and sent back to it.
         */
        const val FORWARDED_CAPTURE_NOTICE =
            "This app does not report where you tap: taps are now captured and forwarded to it, so they land a moment after you release"
    }
}
