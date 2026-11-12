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
import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager

/**
 * Records taps performed by the user while they keep using their apps.
 *
 * The recording window is a one pixel non touchable view receiving the touches made outside of it
 * (`FLAG_WATCH_OUTSIDE_TOUCH`): the system then delivers one `ACTION_OUTSIDE` event per gesture with its screen
 * coordinates, while the underlying app still receives the touch. This is the same technique the vendored engine uses to
 * detect user touches, see `UserTouchWatcher`.
 *
 * Only taps can be recorded this way: Android does not report duration, movement or additional fingers for touches
 * happening outside of the window. On a device that reports no position for these touches, and in an app that draws
 * nothing accessible, nothing at all can be recorded: `RecordingController` then switches to `RelayRecordingOverlay`.
 */
class TapRecordingOverlay(
    private val service: AccessibilityService,
    private val onTapRecorded: (x: Float, y: Float, eventTimeMs: Long) -> Unit,
    private val onStopRequested: () -> Unit,
) {

    private val windowManager: WindowManager? = service.getSystemService(WindowManager::class.java)

    private var watcherView: View? = null

    private val indicator = RecordingIndicator(service = service)
    private val stopControl = RecordingStopControl(service = service, onStopRequested = onStopRequested)

    fun start() {
        val windowManager = windowManager ?: return

        indicator.show()

        if (watcherView == null) {
            @SuppressLint("ClickableViewAccessibility") // The view only receives the touches made outside of it.
            val view = View(service).apply {
                setOnTouchListener { _, event -> onWatcherTouchEvent(event) }
            }

            if (addView(windowManager, view, watcherLayoutParams())) {
                watcherView = view
            }
        }

        stopControl.show()
    }

    fun stop() {
        indicator.hide()
        stopControl.hide()

        val windowManager = windowManager ?: return

        watcherView?.let { view -> removeView(windowManager, view) }
        watcherView = null
    }
    /**
     * The system sends one `ACTION_OUTSIDE` per gesture made outside of the watcher window, while the app under the
     * finger still receives the touch.
     *
     * Its coordinates are the screen ones on most devices, but some report `(0,0)` for every touch (Samsung): the event
     * coordinates are then used, and `RecordingController` falls back to the click of the view that received the tap.
     */
    private fun onWatcherTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_OUTSIDE) return false

        val hasRawCoordinates = event.rawX != 0f || event.rawY != 0f
        val x = if (hasRawCoordinates) event.rawX else event.x
        val y = if (hasRawCoordinates) event.rawY else event.y

        Log.d(TAG, "Outside touch at ($x, $y), raw=(${event.rawX}, ${event.rawY})")
        onTapRecorded(x, y, event.eventTime)

        return false
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

    private fun watcherLayoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WATCHER_WINDOW_SIZE_PX,
            WATCHER_WINDOW_SIZE_PX,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

    private companion object {
        const val WATCHER_WINDOW_SIZE_PX = 1
        const val TAG = "TapRecordingOverlay"
    }
}