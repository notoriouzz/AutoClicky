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

import nl.tartaruzz.autoclicky.engine.EngineController
import nl.tartaruzz.autoclicky.recording.RecordingController

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives the floating [QuickPanelOverlay], the minimal control bar displayed above every other app.
 *
 * It lets the user start a recording, stop a recording or a running template, and play the last template without going
 * back to the AutoClicky activity, which is what makes "perform an action in another app and record it" possible.
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

    private var stateJob: Job? = null
    private var completedRecordingJob: Job? = null

    private var isVisibleRequested = false
    private var isDismissedForCurrentRun = false
    private var hasReportedPanelFailure = false
    private var lastState = PanelState()

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
                    hasScenarios = scenarios.isNotEmpty(),
                )
            }.collect { state -> onPanelState(state) }
        }

        completedRecordingJob?.cancel()
        completedRecordingJob = scope.launch {
            recordingController.completedRecording.collect { completed ->
                if (completed != null) {
                    showMessage("Recording captured, open AutoClicky to save it")
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
        val hasScenarios: Boolean = false,
    )
}
