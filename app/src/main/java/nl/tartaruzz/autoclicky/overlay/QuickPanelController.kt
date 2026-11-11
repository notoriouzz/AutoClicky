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

    private var stateJob: Job? = null
    private var completedRecordingJob: Job? = null

    private var isVisibleRequested = false
    private var isDismissedForCurrentRun = false
    private var hasReportedPanelFailure = false
    private var lastState = PanelState()

    /** The recording currently offered in [SaveRecordingOverlay], so it is not displayed twice. */
    private var offeredRecording: CompletedRecording? = null

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
            ) { isRunning, isRecording, scenarios ->
                PanelState(
                    isRunning = isRunning,
                    isRecording = isRecording,
                    scenarios = scenarios,
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

        saveOverlay?.dismiss()
        saveOverlay = null
        pickerOverlay?.dismiss()
        pickerOverlay = null
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
        val shouldBeVisible = !state.isRecording &&
            (isVisibleRequested || (state.isRunning && !isDismissedForCurrentRun))

        if (state.isRecording) {
            // The picker would sit on top of the capture overlay and cannot play anything during a capture.
            pickerOverlay?.dismiss()
            pickerOverlay = null
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
            canList = !state.isRecording,
        )
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
            onStopClicked = ::onStopClicked,
            onPlayClicked = ::onPlayClicked,
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

    private fun onListClicked() {
        val service = service ?: return

        pickerOverlay?.dismiss()

        val picker = ScenarioPickerOverlay(
            service = service,
            scenarios = lastState.scenarios,
            isScenarioEnabled = !lastState.isRunning && !lastState.isRecording,
            onScenarioSelected = ::onScenarioSelected,
            onDismissed = { pickerOverlay = null },
        )
        pickerOverlay = picker

        if (!picker.show()) {
            pickerOverlay = null
            showMessage("Cannot display the template list, is the accessibility service enabled?")
        }
    }

    private fun onScenarioSelected(scenario: DumbScenario) {
        pickerOverlay?.dismiss()
        pickerOverlay = null

        scope.launch {
            if (!engineController.startScenario(scenario.id.databaseId)) {
                showMessage("Cannot play this template, is the accessibility service enabled?")
            }
        }
    }

    /** Offer the recording that was just captured, above the app the user is in. */
    private fun showSaveOverlay(recording: CompletedRecording) {
        val service = service ?: return

        offeredRecording = recording
        saveOverlay?.dismiss()

        val overlay = SaveRecordingOverlay(
            service = service,
            actionCount = recording.actions.size,
            recordingName = defaultRecordingName(),
            onSave = { saveRecording(recording) },
            onCancel = {
                saveOverlay?.dismiss()
                saveOverlay = null
            },
        )
        saveOverlay = overlay

        if (!overlay.show()) {
            saveOverlay = null
            showMessage("Cannot display the save dialog, open AutoClicky to save the recording")
        }
    }

    private fun saveRecording(recording: CompletedRecording) {
        saveOverlay?.dismiss()
        saveOverlay = null

        val templateName = defaultRecordingName()

        scope.launch {
            val scenarioId = engineController.addScenario(DumbScenarioBuilder.build(templateName, recording.actions))
            recordingController.consumeCompletedRecording()

            if (scenarioId == null) {
                showMessage("Could not save the recording")
            } else {
                showMessage("Saved: $templateName")
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
    ) {
        val hasScenarios: Boolean get() = scenarios.isNotEmpty()
    }
}
