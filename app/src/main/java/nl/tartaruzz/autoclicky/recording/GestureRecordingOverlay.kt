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
 * Records full gestures (taps, long presses, swipes and multi-finger gestures) on a dimmed overlay covering the screen.
 *
 * Unlike [TapRecordingOverlay], this overlay does receive the touches, so the app underneath cannot be used while
 * recording: the user taps, swipes and pinches on the dimmed screen to describe what the template must replay.
 */
class GestureRecordingOverlay(
    private val service: AccessibilityService,
    private val onSamplesRecorded: (List<TouchSample>) -> Unit,
    private val onStopRequested: () -> Unit,
) {

    private val windowManager: WindowManager? = service.getSystemService(WindowManager::class.java)

    private var captureView: View? = null
    private var stopButton: Button? = null

    private val badge = RecordingBadge(
        service = service,
        label = "● Recording GESTURES\ndraw on the screen, then tap STOP",
    )

    fun start() {
        val windowManager = windowManager ?: return

        if (captureView == null) {
            @SuppressLint("ClickableViewAccessibility")
            val view = object : View(service) {
                override fun onTouchEvent(event: MotionEvent): Boolean {
                    onSamplesRecorded(event.toTouchSamples())
                    return true
                }
            }.apply {
                setBackgroundColor(OVERLAY_COLOR)
            }

            if (addView(windowManager, view, captureLayoutParams())) {
                captureView = view
            }
        }

        if (stopButton == null) {
            val button = Button(service).apply {
                text = "Stop recording"
                setOnClickListener { onStopRequested() }
            }

            if (addView(windowManager, button, stopButtonLayoutParams())) {
                stopButton = button
            }
        }

        badge.show()
    }

    fun stop() {
        badge.hide()

        val windowManager = windowManager ?: return

        captureView?.let { view -> removeView(windowManager, view) }
        captureView = null

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

    private fun captureLayoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
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
        const val OVERLAY_COLOR = 0x33000000
        const val STOP_BUTTON_MARGIN_BOTTOM_PX = 250
    }
}
