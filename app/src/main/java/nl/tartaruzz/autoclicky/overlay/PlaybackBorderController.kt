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

package nl.tartaruzz.autoclicky.overlay

import android.accessibilityservice.AccessibilityService
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

import nl.tartaruzz.autoclicky.engine.EngineController

import javax.inject.Inject
import javax.inject.Singleton

import kotlin.math.roundToInt

/**
 * Marks a running playback above every other app.
 *
 * While a template plays, exactly two AutoClicky elements are shown:
 * - a thin, slowly blinking green frame around the whole screen, with a subtle glow,
 * - a small green play glyph, as a purely visual indicator.
 *
 * Both are windows of type `TYPE_ACCESSIBILITY_OVERLAY` that are not focusable and not touchable, so the touches keep
 * going to the app underneath: a tap recorded on the play glyph still reaches the app, and touching the screen can still
 * stop the playback (see [TouchStopController]).
 */
@Singleton
class PlaybackBorderController @Inject constructor(
    private val engineController: EngineController,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var service: AccessibilityService? = null
    private var borderView: View? = null
    private var borderBlinkAnimator: ObjectAnimator? = null
    private var playIndicatorView: View? = null
    private var observerJob: Job? = null

    private val glowPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    private val borderPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    fun attach(service: AccessibilityService) {
        this.service = service
        observeEngine()
    }

    fun detach() {
        observerJob?.cancel()
        observerJob = null
        hideAll()
        service = null
    }

    private fun observeEngine() {
        observerJob?.cancel()
        observerJob = scope.launch {
            engineController.isRunning.collect { isRunning ->
                if (isRunning) showAll() else hideAll()
            }
        }
    }

    private fun showAll() {
        showBorder()
        showPlayIndicator()
    }

    private fun hideAll() {
        hideBorder()
        hidePlayIndicator()
    }

    private fun showBorder() {
        val service = service ?: return
        if (borderView != null) return

        val view = object : View(service) {
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)

                val density = resources.displayMetrics.density
                val crispPx = BORDER_WIDTH_DP * density
                val inset = crispPx / 2f
                val rect = RectF(inset, inset, width - inset, height - inset)

                // A soft glow under the thin line: two wide, translucent halos.
                glowPaint.color = BORDER_COLOR
                glowPaint.alpha = GLOW_OUTER_ALPHA
                glowPaint.strokeWidth = crispPx * GLOW_OUTER_MULTIPLIER
                canvas.drawRect(rect, glowPaint)

                glowPaint.alpha = GLOW_INNER_ALPHA
                glowPaint.strokeWidth = crispPx * GLOW_INNER_MULTIPLIER
                canvas.drawRect(rect, glowPaint)

                // The thin, crisp frame.
                borderPaint.color = BORDER_COLOR
                borderPaint.strokeWidth = crispPx
                canvas.drawRect(rect, borderPaint)
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        val windowManager = service.getSystemService(WindowManager::class.java)

        val added = try {
            windowManager.addView(view, params)
            true
        } catch (_: IllegalStateException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }

        if (!added) {
            android.util.Log.w(TAG, "Cannot display the playback frame, the window was refused")
            return
        }

        borderView = view
        startBlinking(view)
    }

    private fun hideBorder() {
        borderBlinkAnimator?.cancel()
        borderBlinkAnimator = null

        val view = borderView ?: return
        borderView = null

        try {
            service?.getSystemService(WindowManager::class.java)?.removeView(view)
        } catch (_: IllegalArgumentException) {
            // The view was already removed.
        }
    }

    private fun showPlayIndicator() {
        val service = service ?: return
        if (playIndicatorView != null) return

        val sizePx = toPx(PLAY_INDICATOR_SIZE_DP)

        val view = TextView(service).apply {
            text = "\u25B6"
            setTextColor(Color.WHITE)
            textSize = PLAY_INDICATOR_GLYPH_SP
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(PLAY_INDICATOR_COLOR)
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
            gravity = Gravity.TOP or Gravity.RIGHT
            x = -toPx(PLAY_INDICATOR_MARGIN_DP)
            y = toPx(PLAY_INDICATOR_MARGIN_DP)
        }

        val windowManager = service.getSystemService(WindowManager::class.java)

        val added = try {
            windowManager.addView(view, params)
            true
        } catch (_: IllegalStateException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }

        if (added) {
            playIndicatorView = view
        } else {
            android.util.Log.w(TAG, "Cannot display the play indicator, the window was refused")
        }
    }

    private fun hidePlayIndicator() {
        val view = playIndicatorView ?: return
        playIndicatorView = null

        try {
            service?.getSystemService(WindowManager::class.java)?.removeView(view)
        } catch (_: IllegalArgumentException) {
            // The view was already removed.
        }
    }

    private fun startBlinking(view: View) {
        borderBlinkAnimator = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, MIN_ALPHA).apply {
            duration = BLINK_DURATION_MS
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }
    }

    private fun toPx(valueDp: Int): Int =
        (valueDp * (service?.resources?.displayMetrics?.density ?: 1f)).roundToInt()

    private companion object {
        val BORDER_COLOR: Int = Color.parseColor("#2E8B2E")
        val PLAY_INDICATOR_COLOR: Int = Color.parseColor("#2E7D32")

        /** Much thinner than the previous frame, with a soft halo around it (see [GLOW_OUTER_MULTIPLIER]). */
        const val BORDER_WIDTH_DP = 3f

        const val GLOW_OUTER_MULTIPLIER = 7f
        const val GLOW_INNER_MULTIPLIER = 3.5f
        const val GLOW_OUTER_ALPHA = 55
        const val GLOW_INNER_ALPHA = 95

        const val BLINK_DURATION_MS = 900L
        const val MIN_ALPHA = 0.25f

        const val PLAY_INDICATOR_SIZE_DP = 22
        const val PLAY_INDICATOR_MARGIN_DP = 24
        const val PLAY_INDICATOR_GLYPH_SP = 13f

        const val TAG = "PlaybackBorderController"
    }
}
