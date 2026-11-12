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
import android.accessibilityservice.GestureDescription
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/**
 * Records the gestures the user performs in their apps, and sends them back to the app they are performed in.
 *
 * This is the capture used when the pass-through watcher of [TapRecordingOverlay] cannot work: Android reports no
 * position for the `ACTION_OUTSIDE` touches on several devices (Samsung reports `0,0` for every touch), and an app that
 * draws nothing accessible — a canvas game, a video player, a custom interface — never reports a click on its views.
 * Neither of them can be fixed without root: the only way to know where a finger went is to receive the touches, and the
 * only way to give them back to the app is to inject them with
 * [AccessibilityService.dispatchGesture].
 *
 * The overlay covers the screen but draws nothing, so the app stays visible and its own windows (the panel, the ✕
 * button, the indicator) stay above it.
 *
 * Every gesture is captured from its first down to its last up, then dispatched. The window is made not touchable while
 * the gesture is injected: otherwise the injected touches would come back to this overlay instead of reaching the app.
 * Two consequences, both inherent to the technique:
 * - the app reacts when the user releases, not while they press;
 * - a touch made while the previous gesture is being sent back (about a tenth of a second, measured on a Galaxy S22
 *   with Android 16) goes straight to the app and is not recorded.
 */
class RelayRecordingOverlay(
    private val service: AccessibilityService,
    private val onSamplesRecorded: (List<TouchSample>) -> Unit,
    private val onStopRequested: () -> Unit,
) {

    private val windowManager: WindowManager? = service.getSystemService(WindowManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var captureView: View? = null
    private var captureParams: WindowManager.LayoutParams? = null

    private val indicator = RecordingIndicator(service = service)
    private val stopControl = RecordingStopControl(service = service, onStopRequested = onStopRequested)

    /** Samples of the gesture being performed: from its first down to its last up. */
    private val gestureSamples = mutableListOf<TouchSample>()

    /** True while the captured gesture is injected in the app: the overlay does not receive anything meanwhile. */
    private var isForwarding = false

    fun start() {
        val windowManager = windowManager ?: return

        if (captureView == null) {
            val view = object : View(service) {
                override fun onTouchEvent(event: MotionEvent): Boolean = onCaptureTouchEvent(event)

                // The window is not clickable: it exists to receive the touches that are recorded, and every touch
                // event is answered by onTouchEvent.
                override fun performClick(): Boolean = super.performClick()
            }

            val params = captureLayoutParams()
            if (addView(windowManager, view, params)) {
                captureView = view
                captureParams = params
            }
        }

        stopControl.show()

        indicator.show(FORWARDING_INDICATOR_COLOR)
    }

    fun stop() {
        indicator.hide()
        stopControl.hide()

        val windowManager = windowManager ?: return

        captureView?.let { view -> removeView(windowManager, view) }
        captureView = null
        captureParams = null

        gestureSamples.clear()
        isForwarding = false
    }

    /**
     * Capture the touches of one gesture.
     *
     * The gesture is complete when the last finger is released: its samples are given to the recorder, and it is sent
     * back to the app the finger was on.
     */
    private fun onCaptureTouchEvent(event: MotionEvent): Boolean {
        if (isForwarding) {
            // Either the gesture that is being forwarded came back to this overlay (the system had not applied the
            // window change yet) or the user started a new touch before it was sent back. In both cases this overlay is
            // the wrong target: recording it would store a duplicate, and the app receives the touches directly again
            // as soon as the forwarded gesture is done.
            val isInjected = event.getToolType(0) == MotionEvent.TOOL_TYPE_UNKNOWN
            Log.w(TAG, "Touch ignored while forwarding: action=${event.actionMasked}, injected=$isInjected")
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> gestureSamples.clear()

            MotionEvent.ACTION_CANCEL -> {
                // The system took the gesture over (a system gesture, the screen turned off): there is nothing to
                // forward, and what was recorded is not a gesture the user performed.
                Log.d(TAG, "Gesture cancelled after ${gestureSamples.size} samples, dropping it")
                gestureSamples.clear()
                return true
            }
        }

        gestureSamples += event.toTouchSamples()

        val isGestureComplete = when (event.actionMasked) {
            MotionEvent.ACTION_UP -> event.pointerCount <= 1
            else -> false
        }
        if (isGestureComplete) completeGesture()

        return true
    }

    /** Hand the gesture to the recorder, then to the app. */
    private fun completeGesture() {
        val samples = gestureSamples.toList()
        gestureSamples.clear()
        if (samples.isEmpty()) return

        onSamplesRecorded(samples)

        if (isForwarding) return

        val collector = StrokeCollector()
        collector.onSamples(samples)
        val stroke = collector.getStrokes().lastOrNull() ?: return

        forward(stroke)
    }

    /**
     * Send the gesture back to the app that is under the captured touches.
     *
     * The capture window must be touchable again as soon as the system is done with the gesture, but not before: while
     * it is not, the app receives the touches directly, as if AutoClicky was not there.
     */
    private fun forward(stroke: RecordedStroke) {
        val specs = RelayGesturePlanner.plan(stroke)
        if (specs.isEmpty()) return

        Log.d(TAG, "Forwarding ${specs.size} stroke(s) starting at ${specs.first().startTimeMs} ms, " +
            "lasting ${specs.first().durationMs} ms")

        isForwarding = true
        setCaptureTouchable(
            touchable = false,
            onApplied = {
                val gesture = RelayGestureBuilder.build(specs)
                if (gesture == null) {
                    isForwarding = false
                    setCaptureTouchable(touchable = true, onApplied = {})
                    return@setCaptureTouchable
                }

                val isDispatched = try {
                    service.dispatchGesture(gesture, replayCallback, mainHandler)
                } catch (e: RuntimeException) {
                    // The system refuses the gesture when its input pipeline is not responsive.
                    Log.w(TAG, "Can't forward the gesture to the app", e)
                    false
                }

                if (!isDispatched) {
                    Log.w(TAG, "The gesture was refused by the system, it is only recorded")
                    isForwarding = false
                    setCaptureTouchable(touchable = true, onApplied = {})
                }
            },
            onTimeout = {
                // The window change was never applied: dispatching now would send the touches back to this overlay
                // instead of the app, and record them a second time.
                Log.w(TAG, "The capture window did not react in time, the gesture is only recorded")
                isForwarding = false
            },
        )
    }

    private val replayCallback = object : AccessibilityService.GestureResultCallback() {
        override fun onCompleted(gestureDescription: GestureDescription?) {
            Log.d(TAG, "Gesture forwarded to the app")
            finishForwarding()
        }

        override fun onCancelled(gestureDescription: GestureDescription?) {
            Log.w(TAG, "The forwarded gesture was cancelled")
            finishForwarding()
        }
    }

    private fun finishForwarding() {
        isForwarding = false
        setCaptureTouchable(touchable = true, onApplied = {})
    }

    /**
     * Make the capture window touchable or not, then run [onApplied].
     *
     * A window change is only visible to the input system once the view hierarchy has been laid out again. Dispatching
     * the gesture before that would send it to this overlay instead of the app underneath, so [onApplied] is called
     * after a traversal. [onTimeout] is called instead when no traversal happened in time.
     */
    private fun setCaptureTouchable(touchable: Boolean, onApplied: () -> Unit, onTimeout: () -> Unit = onApplied) {
        val view = captureView ?: return
        val params = captureParams ?: return
        val windowManager = windowManager ?: return

        params.flags = if (touchable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }

        try {
            windowManager.updateViewLayout(view, params)
        } catch (_: IllegalArgumentException) {
            // The view was removed in the meantime.
            return
        }

        var isApplied = false
        val applyOnce = {
            if (!isApplied) {
                isApplied = true
                if (captureView === view) onApplied()
            }
        }
        val applyTimeout = {
            if (!isApplied) {
                isApplied = true
                if (captureView === view) onTimeout()
            }
        }

        // The animation callback of the next frame runs before its traversal, the posted runnable after it. The system
        // propagates the new window attributes to the input pipeline asynchronously, hence the extra delay before the
        // caller sends anything to the screen.
        view.postOnAnimation { view.postDelayed(applyOnce, WINDOW_CHANGE_SETTLE_MS) }
        view.postDelayed(applyTimeout, WINDOW_CHANGE_TIMEOUT_MS)
    }

    private fun addView(windowManager: WindowManager, view: View, params: WindowManager.LayoutParams): Boolean =
        try {
            windowManager.addView(view, params)
            true
        } catch (e: IllegalStateException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }

    private fun removeView(windowManager: WindowManager, view: View) {
        try {
            windowManager.removeView(view)
        } catch (_: IllegalArgumentException) {
            // The view was already removed.
        }
    }

    /**
     * The capture window: full screen, transparent, and touchable.
     *
     * It is deliberately not `FLAG_NOT_TOUCH_MODAL`: every touch of the screen belongs to the recording. It doesn't draw
     * anything, so the app below stays visible, and it must not be `FLAG_NOT_TOUCHABLE` or it would not receive the
     * touches it has to record.
     */
    private fun captureLayoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

    private companion object {
        /**
         * The dot is amber instead of red in this mode: the app now receives the touches from AutoClicky, so it answers
         * when the user releases instead of while they press. Toasts are not available on every device, this marker is.
         */
        val FORWARDING_INDICATOR_COLOR: Int = Color.parseColor("#E6E08A00")

        /**
         * Time left to the system to propagate a window change to its input pipeline.
         *
         * Without it, a gesture dispatched right after the capture window is made not touchable can still be delivered
         * to it instead of the app: the touches would be lost for the app, and recorded twice.
         */
        const val WINDOW_CHANGE_SETTLE_MS = 50L

        /** Longest wait for the window change to be applied before giving up on forwarding the gesture. */
        const val WINDOW_CHANGE_TIMEOUT_MS = 400L

        const val TAG = "RelayRecordingOverlay"
    }
}
