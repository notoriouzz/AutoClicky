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
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

import kotlin.math.roundToInt

/**
 * The small ✕ button that ends a capture, displayed right under the blinking [RecordingIndicator].
 *
 * The visible glyph is about as large as the dot, but the whole window is a 48 dp square: the touch target stays
 * comfortable while the visual stays discreet. The window is anchored to the top end corner with a gravity, like the
 * dot, so a rotation while recording puts it back under the dot by itself.
 */
internal class RecordingStopControl(
    private val service: AccessibilityService,
    private val onStopRequested: () -> Unit,
) {

    private val windowManager: WindowManager? = service.getSystemService(WindowManager::class.java)

    private var stopView: View? = null

    /** Attach the ✕ button. Returns false when the window manager refused the window. */
    fun show(): Boolean {
        val windowManager = windowManager ?: return false
        if (stopView != null) return true

        val container = FrameLayout(service).apply {
            isClickable = true
            isFocusable = true
            contentDescription = "Stop recording"
            setOnClickListener { onStopRequested() }

            addView(buildGlyph(), FrameLayout.LayoutParams(toPx(GLYPH_SIZE_DP), toPx(GLYPH_SIZE_DP), Gravity.CENTER))
        }

        val params = WindowManager.LayoutParams(
            toPx(TOUCH_SIZE_DP),
            toPx(TOUCH_SIZE_DP),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // Same top-end anchor as the dot; the two windows are centred on each other.
            gravity = Gravity.TOP or Gravity.RIGHT
            x = -(toPx(CORNER_MARGIN_DP) - (toPx(TOUCH_SIZE_DP) - toPx(INDICATOR_SIZE_DP)) / 2)
            y = toPx(CORNER_MARGIN_DP) + toPx(INDICATOR_SIZE_DP) + toPx(GAP_DP)
        }

        val added = try {
            windowManager.addView(container, params)
            true
        } catch (_: IllegalStateException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }

        if (!added) return false

        stopView = container
        return true
    }

    /** Detach the ✕ button. */
    fun hide() {
        val windowManager = windowManager ?: return
        val view = stopView ?: return

        stopView = null

        try {
            windowManager.removeView(view)
        } catch (_: IllegalArgumentException) {
            // The view was already removed.
        }
    }

    private fun buildGlyph(): TextView = TextView(service).apply {
        text = "\u2715"
        setTextColor(Color.WHITE)
        textSize = GLYPH_TEXT_SIZE_SP
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        includeFontPadding = false
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(GLYPH_BACKGROUND_COLOR)
        }
    }

    private fun toPx(valueDp: Int): Int = (valueDp * service.resources.displayMetrics.density).roundToInt()

    private companion object {
        val GLYPH_BACKGROUND_COLOR: Int = Color.parseColor("#B3242424")
        val GLYPH_COLOR: Int = Color.WHITE

        /** Same values as [RecordingIndicator], so the ✕ sits right under the dot. */
        const val INDICATOR_SIZE_DP = 18
        const val CORNER_MARGIN_DP = 24
        const val GAP_DP = 6

        const val TOUCH_SIZE_DP = 48
        const val GLYPH_SIZE_DP = 24
        const val GLYPH_TEXT_SIZE_SP = 16f
    }
}
