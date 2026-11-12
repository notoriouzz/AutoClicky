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
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button

/**
 * Records taps performed by the user while they keep using their apps.
 *
 * The recording window is a one pixel non touchable view receiving the touches made outside of it
 * (`FLAG_WATCH_OUTSIDE_TOUCH`): the system then delivers one `ACTION_OUTSIDE` event per gesture with its screen
 * coordinates, while the underlying app still receives the touch. This is the same technique the vendored engine uses to
 * detect user touches, see `UserTouchWatcher`.
 *
 * Only taps can be recorded this way: Android does not report duration, movement or additional fingers for touches
 * happening outside of the window.
 */
class TapRecordingOverlay(
    private val service: AccessibilityService,
    private val onTapRecorded: (x: Float, y: Float, eventTimeMs: Long) -> Unit,
    private val onStopRequested: () -> Unit,
) {

    private val windowManager: WindowManager? = service.getSystemService(WindowManager::class.java)

    private var watcherView: View? = null
    private var stopButton: Button? = null

    private val indicator = RecordingIndicator(service = service)

    fun start() {
        val windowManager = windowManager ?: return

        indicator.show()

        if (watcherView == null) {
            @SuppressLint("ClickableViewAccessibility") // The view only receives the touches made outside of it.
            val view = View(service).apply {
                setOnTouchListener { _, event ->
                    if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) {
                        onTapRecorded(event.rawX, event.rawY, event.eventTime)
                    }
                    false
                }
            }

            if (addView(windowManager, view, watcherLayoutParams())) {
                watcherView = view
            }
        }

        if (stopButton == null) {
            val button = Button(service).apply {
                text = "Stop"
                setOnClickListener { onStopRequested() }
            }

            if (addView(windowManager, button, stopButtonLayoutParams())) {
                stopButton = button
            }
        }
    }

    fun stop() {
        indicator.hide()

        val windowManager = windowManager ?: return

        watcherView?.let { view -> removeView(windowManager, view) }
        watcherView = null

        stopButton?.let { view -> removeView(windowManager, view) }
        stopButton = null
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

    private fun stopButtonLayoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = STOP_BUTTON_MARGIN_BOTTOM_PX
        }

    private companion object {
        const val WATCHER_WINDOW_SIZE_PX = 1
        const val STOP_BUTTON_MARGIN_BOTTOM_PX = 250
    }
}
