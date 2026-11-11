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

package nl.tartaruzz.autoclicky.recording

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

import kotlin.math.abs

/**
 * Drives the recording overlays and exposes the finished recording to the UI.
 *
 * The overlays need the running accessibility service, so [attach] is called by it on connection and [detach] on
 * unbinding. Everything else (activity, overlays) goes through this single instance.
 */
@Singleton
class RecordingController @Inject constructor() {

    private val _isRecording = MutableStateFlow(false)

    /** True while an overlay is capturing the user inputs. */
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _completedRecording = MutableStateFlow<CompletedRecording?>(null)

    /** Set as soon as a recording stops with at least one action. Consume it with [consumeCompletedRecording]. */
    val completedRecording: StateFlow<CompletedRecording?> = _completedRecording

    private var service: AccessibilityService? = null

    private var tapOverlay: TapRecordingOverlay? = null
    private var gestureOverlay: GestureRecordingOverlay? = null

    private var strokeCollector: StrokeCollector? = null
    private val tapActions = mutableListOf<RecordedAction.Tap>()

    fun attach(service: AccessibilityService) {
        this.service = service
    }

    fun detach() {
        stopCapture()
        service = null
        _completedRecording.value = null
    }

    /** Record the taps the user makes while using their apps. Returns false if no overlay could be shown. */
    fun startTapCapture(): Boolean {
        val service = service ?: return false
        if (_isRecording.value) return false

        tapActions.clear()

        val overlay = TapRecordingOverlay(
            service = service,
            onTapRecorded = ::onTapRecorded,
            onStopRequested = { stopCapture() },
        )
        overlay.start()

        tapOverlay = overlay
        _isRecording.value = true
        return true
    }

    /** Record full gestures on a dimmed overlay. Returns false if no overlay could be shown. */
    fun startGestureCapture(): Boolean {
        val service = service ?: return false
        if (_isRecording.value) return false

        tapActions.clear()

        val collector = StrokeCollector()
        val overlay = GestureRecordingOverlay(
            service = service,
            onSamplesRecorded = { samples -> collector.onSamples(samples) },
            onStopRequested = { stopCapture() },
        )
        overlay.start()

        strokeCollector = collector
        gestureOverlay = overlay
        _isRecording.value = true
        return true
    }

    /** Stop the current capture, and publish the [completedRecording] if anything was recorded. */
    fun stopCapture() {
        if (!_isRecording.value) return
        _isRecording.value = false

        tapOverlay?.stop()
        tapOverlay = null

        gestureOverlay?.stop()
        gestureOverlay = null

        val actions = if (tapActions.isNotEmpty()) {
            ScenarioRecorder.tapsToRecordedActions(tapActions.toList())
        } else {
            ScenarioRecorder.toRecordedActions(strokeCollector?.getStrokes().orEmpty())
        }

        tapActions.clear()
        strokeCollector = null

        if (actions.isNotEmpty()) {
            _completedRecording.value = CompletedRecording(actions)
        }
    }

    fun consumeCompletedRecording(): CompletedRecording? {
        val completed = _completedRecording.value
        _completedRecording.value = null
        return completed
    }

    /**
     * Record the click of a view, reported by the accessibility service.
     *
     * This is the reliable source for the position of a tap: `ACTION_OUTSIDE` reports no coordinates on several devices
     * (Samsung reports 0,0), while the click event carries the screen bounds of the view that was clicked.
     */
    fun onViewClicked(event: AccessibilityEvent) {
        if (!_isRecording.value) return

        val source = event.source ?: return
        val bounds = Rect()

        try {
            source.getBoundsInScreen(bounds)
        } catch (_: IllegalStateException) {
            // The node has been detached in the meantime.
            return
        }

        if (bounds.isEmpty) return

        addTap(bounds.exactCenterX(), bounds.exactCenterY(), event.eventTime, fromView = true)
    }

    private fun onTapRecorded(x: Float, y: Float, eventTimeMs: Long) {
        addTap(x, y, eventTimeMs, fromView = false)
    }

    /**
     * Store a tap, keeping a single action per gesture.
     *
     * A gesture is reported twice: by the outside touch watcher and by the accessibility click event of the view that
     * received it. The view bounds are preferred, and a touch reported at (0,0) (no coordinates available) is dropped.
     */
    private fun addTap(x: Float, y: Float, eventTimeMs: Long, fromView: Boolean) {
        if (x == 0f && y == 0f) return

        val existingIndex = tapActions.indexOfLast { tap -> abs(tap.startTimeMs - eventTimeMs) <= TAP_DEDUP_DURATION_MS }
        if (existingIndex >= 0) {
            if (fromView) tapActions[existingIndex] = tapAt(x, y, eventTimeMs)
            return
        }

        tapActions.add(tapAt(x, y, eventTimeMs))
    }

    private fun tapAt(x: Float, y: Float, eventTimeMs: Long): RecordedAction.Tap =
        RecordedAction.Tap(
            startTimeMs = eventTimeMs,
            endTimeMs = eventTimeMs + TAP_PRESS_DURATION_MS,
            x = x,
            y = y,
        )

    private companion object {
        /** The outside touches do not carry their duration: use the same default as the quick click scenario. */
        const val TAP_PRESS_DURATION_MS = 50L

        /** A touch and the click event of the same gesture never arrive more than this apart. */
        const val TAP_DEDUP_DURATION_MS = 300L
    }
}
