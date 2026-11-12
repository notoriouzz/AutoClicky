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
import android.util.Log
import android.view.accessibility.AccessibilityEvent

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drives the recording overlays and exposes the finished recording to the UI.
 *
 * The overlays need the running accessibility service, so [attach] is called by it on connection and [detach] on
 * unbinding. Everything else (activity, overlays) goes through this single instance.
 *
 * A tap capture starts with [TapRecordingOverlay]: the touches of the user go through to the app and are observed from
 * the outside, which costs nothing in latency but cannot see where the finger went on every device, nor in an app that
 * draws nothing accessible. When that happens, the capture switches to [RelayRecordingOverlay] for the rest of the
 * session: the touches are then received by AutoClicky and sent back to the app, which works everywhere but delays the
 * app by the duration of the gesture. Both are the same mode for the user: **REC** records what they do in their apps.
 */
@Singleton
class RecordingController @Inject constructor() {

    private val _isRecording = MutableStateFlow(false)

    /** True while an overlay is capturing the user inputs. */
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _completedRecording = MutableStateFlow<CompletedRecording?>(null)

    /** Set as soon as a recording stops with at least one action. Consume it with [consumeCompletedRecording]. */
    val completedRecording: StateFlow<CompletedRecording?> = _completedRecording

    private val _recordingEmptyNotice = MutableStateFlow(false)

    /**
     * True once a recording stopped with nothing to save, until [acknowledgeEmptyRecording] is called. It is the only
     * way to tell an empty capture from a capture that never started.
     */
    val recordingEmptyNotice: StateFlow<Boolean> = _recordingEmptyNotice

    private val _isForwardingTouches = MutableStateFlow(false)

    /**
     * True while the tap capture forwards the touches back to the app instead of letting them pass through to it.
     *
     * The user is told about it: the app now reacts when they release instead of while they press, and every touch made
     * while a gesture is forwarded goes to the app without being recorded.
     */
    val isForwardingTouches: StateFlow<Boolean> = _isForwardingTouches

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Waits for the click event of a tap before deciding that the pass-through capture cannot work. */
    private var relayPromotionJob: Job? = null

    private var service: AccessibilityService? = null

    private var tapOverlay: TapRecordingOverlay? = null
    private var gestureOverlay: GestureRecordingOverlay? = null
    private var relayOverlay: RelayRecordingOverlay? = null

    private var strokeCollector: StrokeCollector? = null

    /** True while the tap capture runs, false while the gesture overlay does. */
    private var isTapCapture: Boolean = false

    /** True when the tap capture relays the touches to the app instead of letting them go through to it. */
    private var isRelayCapture: Boolean = false

    /** Packages of the apps that reported a click: proves that the app in front exposes clickable views. */
    private val clickingPackages = mutableSetOf<String>()

    /** Positions reported by the outside touch watcher, one per gesture. */
    private val watcherTaps = mutableListOf<RecordedTouch>()

    /** Taps reported by the accessibility click events, with the bounds of the view that received them. */
    private val viewClicks = mutableListOf<RecordedViewClick>()

    fun attach(service: AccessibilityService) {
        this.service = service
    }

    fun detach() {
        stopCapture()
        cancelRelayPromotion()
        service = null
        _completedRecording.value = null
        _recordingEmptyNotice.value = false
        _isForwardingTouches.value = false
    }

    /** Record the taps the user makes while using their apps. Returns false if no overlay could be shown. */
    fun startTapCapture(): Boolean {
        val service = service ?: return false
        if (_isRecording.value) return false

        clearTaps()
        cancelRelayPromotion()
        isRelayCapture = false
        _isForwardingTouches.value = false

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
        cancelRelayPromotion()
        isRelayCapture = false
        _isForwardingTouches.value = false

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

        cancelRelayPromotion()

        tapOverlay?.stop()
        tapOverlay = null

        gestureOverlay?.stop()
        gestureOverlay = null

        relayOverlay?.stop()
        relayOverlay = null

        val taps = if (isRelayCapture) emptyList() else mergedTaps()
        val actions = if (taps.isNotEmpty()) {
            ScenarioRecorder.tapsToRecordedActions(taps)
        } else {
            ScenarioRecorder.toRecordedActions(strokeCollector?.getStrokes().orEmpty())
        }

        // A relay capture can also hold swipes and long presses, which are worth naming as such in the save card.
        val mode = when {
            !isTapCapture -> RecordingMode.GESTURES
            actions.any { action -> action is RecordedAction.Gesture } -> RecordingMode.GESTURES
            else -> RecordingMode.TAPS
        }

        Log.i(TAG, "Recording stopped: ${actions.size} action(s), mode=$mode, relayed=$isRelayCapture")
        Log.d(TAG, "Recorded actions: ${actions.joinToString { action -> action.describe() }}")

        clearTaps()
        strokeCollector = null
        isRelayCapture = false
        _isForwardingTouches.value = false

        if (actions.isNotEmpty()) {
            _completedRecording.value = CompletedRecording(actions = actions, mode = mode)
        } else {
            _recordingEmptyNotice.value = true
        }
    }

    fun consumeCompletedRecording(): CompletedRecording? {
        val completed = _completedRecording.value
        _completedRecording.value = null
        return completed
    }

    fun acknowledgeEmptyRecording() {
        _recordingEmptyNotice.value = false
    }

    /**
     * Record the click of a view, reported by the accessibility service.
     *
     * This is the reliable source for the position of a tap: `ACTION_OUTSIDE` reports no coordinates on several devices
     * (Samsung reports 0,0), while the click event carries the screen bounds of the view that was clicked.
     *
     * A long click is also the only way to know that a view was held down: the pass-through watcher has the down time
     * but not the release, the click event has the release but not the down. Both are paired when the capture ends.
     */
    fun onViewClicked(event: AccessibilityEvent) {
        if (!_isRecording.value || !isTapCapture || isRelayCapture) return

        val isLongPress = event.eventType == AccessibilityEvent.TYPE_VIEW_LONG_CLICKED

        val source = event.source ?: return
        val bounds = Rect()

        try {
            source.getBoundsInScreen(bounds)
        } catch (_: IllegalStateException) {
            // The node has been detached in the meantime.
            return
        }

        if (bounds.isEmpty) return

        // The app reports its views: the pass-through capture can place the taps, there is nothing to relay.
        event.packageName?.toString()?.let { packageName -> clickingPackages.add(packageName) }
        cancelRelayPromotion()

        addViewClick(Rect(bounds), event.eventTime, isLongPress)
    }

    /** Position of a tap, reported by the outside touch watcher. It has no duration and no size. */
    private fun onTapRecorded(x: Float, y: Float, eventTimeMs: Long) {
        if (!isTapCapture || isRelayCapture) return

        val touch = RecordedTouch(x = x, y = y, timeMs = eventTimeMs)
        watcherTaps.add(touch)

        if (touch.hasCoordinates) return

        // No position: the click event of the view has to place this tap. Give it the time to arrive, and if it never
        // does, this app cannot be recorded this way.
        scheduleRelayPromotion()
    }

    /**
     * Switch to the relay capture when the pass-through one cannot work here.
     *
     * `ACTION_OUTSIDE` reports the tap at (0,0) on several devices, and an app that draws its interface itself (a game,
     * a canvas, a video player, a WebView page) never reports a click on its views. In that case nothing can place the
     * tap: the only way left is to receive the touches and send them back to the app, see [RelayRecordingOverlay].
     */
    private fun scheduleRelayPromotion() {
        relayPromotionJob?.cancel()
        relayPromotionJob = scope.launch {
            delay(PROMOTION_GRACE_MS)
            if (!isTapCapture || isRelayCapture || !_isRecording.value) return@launch

            if (foregroundAppReportsClicks()) {
                Log.d(TAG, "No touch position, but the app in front reports its views: keeping the pass-through capture")
                return@launch
            }

            promoteToRelay()
        }
    }

    /** True when the app in front already reported a click: its taps can be placed from the bounds of its views. */
    private fun foregroundAppReportsClicks(): Boolean {
        if (clickingPackages.isEmpty()) return false

        val foregroundPackage = try {
            service?.rootInActiveWindow?.packageName?.toString()
        } catch (_: IllegalStateException) {
            // The window is gone, the app below has no accessible content.
            null
        } ?: return false

        return foregroundPackage in clickingPackages
    }

    /** Replace the pass-through watcher by the relay overlay, for the rest of this capture. */
    private fun promoteToRelay() {
        val service = service ?: return
        if (isRelayCapture || !isTapCapture) return

        Log.i(TAG, "No tap position available here, switching the recording to the relay overlay")

        tapOverlay?.stop()
        tapOverlay = null

        // Nothing recorded by the watcher can be placed on the screen: the relay starts from a clean state.
        clearTaps()

        val collector = StrokeCollector()
        val overlay = RelayRecordingOverlay(
            service = service,
            onSamplesRecorded = { samples -> collector.onSamples(samples) },
            onStopRequested = { stopCapture() },
        )
        overlay.start()

        strokeCollector = collector
        relayOverlay = overlay
        isRelayCapture = true
        _isForwardingTouches.value = true
    }

    private fun cancelRelayPromotion() {
        relayPromotionJob?.cancel()
        relayPromotionJob = null
    }

    /** Store the click of a view. The duplicated events of a single gesture are filtered when the taps are merged. */
    private fun addViewClick(bounds: Rect, eventTimeMs: Long, isLongPress: Boolean) {
        if (bounds.isEmpty) return

        viewClicks.add(
            RecordedViewClick(
                left = bounds.left,
                top = bounds.top,
                right = bounds.right,
                bottom = bounds.bottom,
                timeMs = eventTimeMs,
                isLongPress = isLongPress,
            )
        )
    }

    /** One action per gesture, built from the two sources of the capture. */
    private fun mergedTaps(): List<RecordedAction.Tap> = TapMerger.merge(watcherTaps, viewClicks)

    private fun clearTaps() {
        watcherTaps.clear()
        viewClicks.clear()
        clickingPackages.clear()
    }

    private companion object {

        /**
         * Time given to the accessibility click event of a tap to arrive before deciding that the app cannot report
         * where its views are. The click of a normal tap follows the release by a few milliseconds.
         */
        const val PROMOTION_GRACE_MS = 700L

        const val TAG = "RecordingController"
    }
}

/** Compact description of an action, for the logs. */
private fun RecordedAction.describe(): String = when (this) {
    is RecordedAction.Tap -> "tap(${x.toInt()},${y.toInt()},${durationMs}ms)"
    is RecordedAction.Gesture -> "gesture(${fingers.size}f,${durationMs}ms)"
}
