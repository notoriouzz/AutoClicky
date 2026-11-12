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
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.WindowManager

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

import nl.tartaruzz.autoclicky.engine.EngineController

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Draws a continuous green frame around the whole screen while a template is playing.
 *
 * The frame is a single full screen window of type `TYPE_ACCESSIBILITY_OVERLAY`, above every other app but not touchable
 * and not focusable, so the touches keep going to the app underneath and the playback can still be stopped by touching
 * the screen. It draws nothing but its border: the app is not covered.
 */
@Singleton
class PlaybackBorderController @Inject constructor(
    private val engineController: EngineController,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var service: AccessibilityService? = null
    private var borderView: View? = null
    private var observerJob: Job? = null

    fun attach(service: AccessibilityService) {
        this.service = service
        observeEngine()
    }

    fun detach() {
        observerJob?.cancel()
        observerJob = null
        hideBorder()
        service = null
    }

    private fun observeEngine() {
        observerJob?.cancel()
        observerJob = scope.launch {
            engineController.isRunning.collect { isRunning ->
                if (isRunning) showBorder() else hideBorder()
            }
        }
    }

    private fun showBorder() {
        val service = service ?: return
        if (borderView != null) return

        val view = object : View(service) {
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)

                val strokePx = STROKE_WIDTH_DP * resources.displayMetrics.density
                val inset = strokePx / 2f
                paint.strokeWidth = strokePx
                paint.color = BORDER_COLOR
                paint.style = Paint.Style.STROKE

                canvas.drawRect(RectF(inset, inset, width - inset, height - inset), paint)
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

        if (added) {
            borderView = view
        } else {
            android.util.Log.w(TAG, "Cannot display the playback frame, the window was refused")
        }
    }

    private fun hideBorder() {
        val view = borderView ?: return
        borderView = null

        try {
            service?.getSystemService(WindowManager::class.java)?.removeView(view)
        } catch (_: IllegalArgumentException) {
            // The view was already removed.
        }
    }

    private val paint = Paint().apply { isAntiAlias = true }

    private companion object {
        val BORDER_COLOR: Int = Color.parseColor("#E62E8B2E")
        const val STROKE_WIDTH_DP = 12f
        const val TAG = "PlaybackBorderController"
    }
}
