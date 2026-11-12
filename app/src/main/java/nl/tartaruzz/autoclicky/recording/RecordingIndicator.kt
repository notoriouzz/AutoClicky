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
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager

import kotlin.math.roundToInt

/**
 * The small blinking red dot displayed in a corner of the screen while a capture runs.
 *
 * It is the only "recording" marker left on purpose: the **Stop** button below is already there, so no text is needed.
 * The dot blinks slowly (fade in and out) so it catches the eye without covering anything, and its window is not
 * touchable: the app under it keeps working, which is required by the tap recording.
 *
 * The window is anchored to the top end corner with a gravity, never with absolute coordinates, so a rotation of the
 * screen while recording puts it back in the corner by itself.
 */
internal class RecordingIndicator(
    private val service: AccessibilityService,
) {

    private val windowManager: WindowManager? = service.getSystemService(WindowManager::class.java)

    private var indicatorView: View? = null
    private var blinkAnimator: ObjectAnimator? = null

    /** Attach the dot. Returns false when the window manager refused the window. */
    fun show(): Boolean {
        val windowManager = windowManager ?: return false
        if (indicatorView != null) return true

        val sizePx = toPx(INDICATOR_SIZE_DP)

        val dot = View(service).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(INDICATOR_COLOR)
                setStroke(toPx(2), INDICATOR_RING_COLOR)
            }
        }

        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // Gravity does the placement, so the dot follows a rotation: a negative x moves it inside the right edge.
            gravity = Gravity.TOP or Gravity.RIGHT
            x = -toPx(CORNER_MARGIN_DP)
            y = toPx(CORNER_MARGIN_DP)
        }

        val added = try {
            windowManager.addView(dot, params)
            true
        } catch (_: IllegalStateException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }

        if (!added) return false

        indicatorView = dot
        startBlinking(dot)
        return true
    }

    /** Detach the dot and stop the animation. */
    fun hide() {
        blinkAnimator?.cancel()
        blinkAnimator = null

        val windowManager = windowManager ?: return
        val dot = indicatorView ?: return

        indicatorView = null

        try {
            windowManager.removeView(dot)
        } catch (_: IllegalArgumentException) {
            // The view was already removed.
        }
    }

    private fun startBlinking(view: View) {
        blinkAnimator = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, MIN_ALPHA).apply {
            duration = BLINK_DURATION_MS
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun toPx(valueDp: Int): Int = (valueDp * service.resources.displayMetrics.density).roundToInt()

    private companion object {
        val INDICATOR_COLOR: Int = Color.parseColor("#E6C62828")
        val INDICATOR_RING_COLOR: Int = Color.parseColor("#80FFFFFF")

        const val INDICATOR_SIZE_DP = 18
        const val CORNER_MARGIN_DP = 24
        const val MIN_ALPHA = 0.2f
        const val BLINK_DURATION_MS = 650L
    }
}
