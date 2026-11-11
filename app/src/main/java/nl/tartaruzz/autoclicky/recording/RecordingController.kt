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

    /** True while [TapRecordingOverlay] is running, false while the gesture overlay is. */
    private var isTapCapture: Boolean = false

    /** Positions reported by the outside touch watcher, one per gesture. */
    private val watcherTaps = mutableListOf<RecordedTouch>()

    /** Taps reported by the accessibility click events, with the bounds of the view that received them. */
    private val viewClicks = mutableListOf<RecordedViewClick>()

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

        clearTaps()

        val overlay = TapRecordingOverlay(
            service = service,
            onTapRecorded = ::onTapRecorded,
            onStopRequested = { stopCapture() },
        )
        overlay.start()

        tapOverlay = overlay
        isTapCapture = true
        _isRecording.value = true
        return true
    }

    /** Record full gestures on a dimmed overlay. Returns false if no overlay could be shown. */
    fun startGestureCapture(): Boolean {
        val service = service ?: return false
        if (_isRecording.value) return false

        clearTaps()

        val collector = StrokeCollector()
        val overlay = GestureRecordingOverlay(
            service = service,
            onSamplesRecorded = { samples -> collector.onSamples(samples) },
            onStopRequested = { stopCapture() },
        )
        overlay.start()

        strokeCollector = collector
        gestureOverlay = overlay
        isTapCapture = false
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

        val taps = mergedTaps()
        val actions = if (taps.isNotEmpty()) {
            ScenarioRecorder.tapsToRecordedActions(taps)
        } else {
            ScenarioRecorder.toRecordedActions(strokeCollector?.getStrokes().orEmpty())
        }

        val mode = if (isTapCapture) RecordingMode.TAPS else RecordingMode.GESTURES

        clearTaps()
        strokeCollector = null

        if (actions.isNotEmpty()) {
            _completedRecording.value = CompletedRecording(actions = actions, mode = mode)
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
        if (!_isRecording.value || !isTapCapture) return

        val source = event.source ?: return
        val bounds = Rect()

        try {
            source.getBoundsInScreen(bounds)
        } catch (_: IllegalStateException) {
            // The node has been detached in the meantime.
            return
        }

        if (bounds.isEmpty) return

        addViewClick(Rect(bounds), event.eventTime)
    }

    /** Position of a tap, reported by the outside touch watcher. It has no duration and no size. */
    private fun onTapRecorded(x: Float, y: Float, eventTimeMs: Long) {
        if (!isTapCapture) return

        watcherTaps.add(RecordedTouch(x = x, y = y, timeMs = eventTimeMs))
    }

    /** Store the click of a view. The duplicated events of a single gesture are filtered when the taps are merged. */
    private fun addViewClick(bounds: Rect, eventTimeMs: Long) {
        if (bounds.isEmpty) return

        viewClicks.add(
            RecordedViewClick(
                left = bounds.left,
                top = bounds.top,
                right = bounds.right,
                bottom = bounds.bottom,
                timeMs = eventTimeMs,
            )
        )
    }

    /** One action per gesture, built from the two sources of the capture. */
    private fun mergedTaps(): List<RecordedAction.Tap> = TapMerger.merge(watcherTaps, viewClicks)

    private fun clearTaps() {
        watcherTaps.clear()
        viewClicks.clear()
    }
}
