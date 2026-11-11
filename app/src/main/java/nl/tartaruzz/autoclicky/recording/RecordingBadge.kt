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
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

import kotlin.math.roundToInt

/**
 * The red banner displayed at the top of the screen while a capture runs.
 *
 * The two capture modes look alike once the panel is hidden, so the banner is what tells the user whether the taps of
 * the app are being recorded or the gestures drawn on the dimmed overlay. It is a non touchable window: everything
 * under it keeps working, including the app being recorded.
 */
internal class RecordingBadge(
    private val service: AccessibilityService,
    private val label: String,
) {

    private val windowManager: WindowManager? = service.getSystemService(WindowManager::class.java)

    private var badgeView: TextView? = null

    /** Attach the banner. Returns false when the window manager refused the window. */
    fun show(): Boolean {
        val windowManager = windowManager ?: return false
        if (badgeView != null) return true

        val badge = TextView(service).apply {
            this.text = label
            setTextColor(Color.WHITE)
            textSize = TEXT_SIZE_SP
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            setPadding(toPx(16), toPx(10), toPx(16), toPx(10))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = toPx(16).toFloat()
                setColor(BADGE_COLOR)
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = toPx(TOP_MARGIN_DP)
        }

        val added = try {
            windowManager.addView(badge, params)
            true
        } catch (_: IllegalStateException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }

        if (!added) return false

        badgeView = badge
        return true
    }

    /** Detach the banner. */
    fun hide() {
        val windowManager = windowManager ?: return
        val badge = badgeView ?: return

        badgeView = null

        try {
            windowManager.removeView(badge)
        } catch (_: IllegalArgumentException) {
            // The view was already removed.
        }
    }

    private fun toPx(valueDp: Int): Int = (valueDp * service.resources.displayMetrics.density).roundToInt()

    private companion object {
        val BADGE_COLOR: Int = Color.parseColor("#E6C62828")

        const val TEXT_SIZE_SP = 13f
        const val TOP_MARGIN_DP = 120
    }
}
