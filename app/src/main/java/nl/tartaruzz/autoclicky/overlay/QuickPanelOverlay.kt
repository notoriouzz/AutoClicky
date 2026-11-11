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
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

import androidx.core.view.doOnLayout

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Minimal floating control panel, displayed above every other app.
 *
 * The panel is a small rounded bar with REC / STOP / PLAY / ×N / LIST / X buttons. Like the recording overlays it is a
 * [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY] window: this window type belongs to the accessibility service
 * and is exempt from [android.Manifest.permission.SYSTEM_ALERT_WINDOW], so no overlay permission is needed and the panel
 * always ends up on top of whatever app is in the foreground.
 *
 * The panel is draggable: a touch that moves more than the touch slop moves the window instead of clicking the button.
 */
class QuickPanelOverlay(
    private val service: AccessibilityService,
    private val onRecordClicked: () -> Unit,
    private val onStopClicked: () -> Unit,
    private val onPlayClicked: () -> Unit,
    private val onRepeatClicked: () -> Unit,
    private val onListClicked: () -> Unit,
    private val onHideClicked: () -> Unit,
) {

    private val windowManager: WindowManager? = service.getSystemService(WindowManager::class.java)

    private var panelView: LinearLayout? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var isPositioned = false

    private var recordButton: TextView? = null
    private var stopButton: TextView? = null
    private var playButton: TextView? = null
    private var repeatButton: TextView? = null
    private var listButton: TextView? = null

    /** True while the panel window is attached to the window manager. */
    val isShown: Boolean get() = panelView != null

    /** Attach the panel. Returns false when the window manager refused the window. */
    fun show(): Boolean {
        val windowManager = windowManager ?: return false
        if (panelView != null) return true

        val params = panelLayoutParams()
        val panel = buildPanel(params)

        val added = try {
            windowManager.addView(panel, params)
            true
        } catch (_: IllegalStateException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }

        if (!added) return false

        panelView = panel
        panelParams = params

        // The window size is only known once laid out: place it in the top right corner from there.
        panel.doOnLayout { positionPanelIfNeeded(panel) }
        return true
    }

    /** Detach the panel from the window manager. */
    fun hide() {
        val windowManager = windowManager ?: return
        val panel = panelView ?: return

        panelView = null
        panelParams = null
        isPositioned = false
        recordButton = null
        stopButton = null
        playButton = null
        repeatButton = null
        listButton = null

        try {
            windowManager.removeView(panel)
        } catch (_: IllegalArgumentException) {
            // The view was already removed.
        }
    }

    /** Reflect the state of the recorder and of the engine on the panel buttons. */
    fun setEnabledState(
        canRecord: Boolean,
        canStop: Boolean,
        canPlay: Boolean,
        canRepeat: Boolean,
        canList: Boolean,
    ) {
        recordButton?.setButtonEnabled(canRecord)
        stopButton?.setButtonEnabled(canStop)
        playButton?.setButtonEnabled(canPlay)
        repeatButton?.setButtonEnabled(canRepeat)
        listButton?.setButtonEnabled(canList)
    }

    /** Show the number of passes currently chosen on the repeat button. */
    fun setRepeatLabel(label: String) {
        repeatButton?.text = label
    }

    @SuppressLint("ClickableViewAccessibility") // Every button handles its own touch events, see DragTouchListener.
    private fun buildPanel(params: WindowManager.LayoutParams): LinearLayout {
        val panel = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(toPx(PANEL_PADDING_DP), toPx(PANEL_PADDING_DP), toPx(PANEL_PADDING_DP), toPx(PANEL_PADDING_DP))
            background = roundedBackground(PANEL_COLOR, PANEL_CORNER_RADIUS_DP)
            elevation = toPx(PANEL_ELEVATION_DP).toFloat()
            contentDescription = "AutoClicky floating panel"
        }

        recordButton = actionButton("REC", RECORD_COLOR, "Record the taps made in any app", params, onRecordClicked)
        stopButton = actionButton("STOP", STOP_COLOR, "Stop recording or playing", params, onStopClicked)
        playButton = actionButton("PLAY", PLAY_COLOR, "Play the last template", params, onPlayClicked)
        repeatButton = actionButton(
            "1×",
            REPEAT_COLOR,
            "Choose how many times the template is played",
            params,
            onRepeatClicked,
        )
        listButton = actionButton("\u2261", LIST_COLOR, "Choose and play a template", params, onListClicked)
        val hideButton = actionButton("X", HIDE_COLOR, "Hide the AutoClicky panel", params, onHideClicked)

        listOfNotNull(recordButton, stopButton, playButton, repeatButton, listButton, hideButton)
            .forEach { button -> panel.addView(button) }

        return panel
    }

    private fun actionButton(
        label: String,
        backgroundColor: Int,
        description: String,
        params: WindowManager.LayoutParams,
        onClick: () -> Unit,
    ): TextView = TextView(service).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = BUTTON_TEXT_SIZE_SP
        gravity = Gravity.CENTER
        includeFontPadding = false
        contentDescription = description
        background = roundedBackground(backgroundColor, BUTTON_CORNER_RADIUS_DP)
        isClickable = true
        isFocusable = false
        layoutParams = LinearLayout.LayoutParams(toPx(BUTTON_SIZE_DP), toPx(BUTTON_SIZE_DP)).apply {
            marginStart = toPx(BUTTON_SPACING_DP)
        }
        setOnTouchListener(DragTouchListener(params, onClick))
    }

    private fun TextView.setButtonEnabled(enabled: Boolean) {
        isEnabled = enabled
        alpha = if (enabled) 1f else DISABLED_ALPHA
    }

    private fun roundedBackground(color: Int, cornerRadiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = toPx(cornerRadiusDp).toFloat()
            setColor(color)
        }

    private fun panelLayoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = toPx(PANEL_TOP_MARGIN_DP)
        }

    private fun positionPanelIfNeeded(panel: LinearLayout) {
        if (isPositioned) return

        val params = panelParams ?: return
        val windowManager = windowManager ?: return
        if (panel.width == 0) return

        val screenWidth = service.resources.displayMetrics.widthPixels
        params.x = (screenWidth - panel.width - toPx(PANEL_MARGIN_DP)).coerceAtLeast(0)
        params.y = toPx(PANEL_TOP_MARGIN_DP)
        isPositioned = true

        try {
            windowManager.updateViewLayout(panel, params)
        } catch (_: IllegalArgumentException) {
            // The panel was removed before it could be positioned.
        }
    }

    private fun toPx(valueDp: Int): Int = (valueDp * service.resources.displayMetrics.density).roundToInt()

    /**
     * Moves the panel window when the finger drags, and forwards the click when the finger stays on the same spot.
     *
     * Implementing both gestures on the buttons themselves (instead of on the panel) lets the user drag the panel from
     * any of its buttons; a plain [View.OnClickListener] would swallow the movement.
     */
    private inner class DragTouchListener(
        private val params: WindowManager.LayoutParams,
        private val onClick: () -> Unit,
    ) : View.OnTouchListener {

        private val touchSlop = ViewConfiguration.get(service).scaledTouchSlop

        private var downRawX = 0f
        private var downRawY = 0f
        private var startX = 0
        private var startY = 0
        private var isDragging = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            if (!view.isEnabled) return false

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    isDragging = false
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downRawX
                    val deltaY = event.rawY - downRawY

                    if (!isDragging && (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop)) {
                        isDragging = true
                    }
                    if (isDragging) {
                        moveTo(startX + deltaX.toInt(), startY + deltaY.toInt())
                    }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        view.performClick()
                        onClick()
                    }
                    return true
                }

                MotionEvent.ACTION_CANCEL -> return true
            }

            return false
        }

        private fun moveTo(x: Int, y: Int) {
            val windowManager = windowManager ?: return
            val panel = panelView ?: return

            params.x = x
            params.y = y
            isPositioned = true

            try {
                windowManager.updateViewLayout(panel, params)
            } catch (_: IllegalArgumentException) {
                // The panel was removed in the meantime.
            }
        }
    }

    private companion object {
        val PANEL_COLOR: Int = Color.parseColor("#E6202020")
        val RECORD_COLOR: Int = Color.parseColor("#C62828")
        val STOP_COLOR: Int = Color.parseColor("#546E7A")
        val PLAY_COLOR: Int = Color.parseColor("#2E7D32")
        val REPEAT_COLOR: Int = Color.parseColor("#6A1B9A")
        val LIST_COLOR: Int = Color.parseColor("#3949AB")
        val HIDE_COLOR: Int = Color.parseColor("#424242")

        const val PANEL_PADDING_DP = 6
        const val PANEL_CORNER_RADIUS_DP = 22
        const val PANEL_ELEVATION_DP = 8
        const val PANEL_MARGIN_DP = 12
        const val PANEL_TOP_MARGIN_DP = 160

        const val BUTTON_SIZE_DP = 40
        const val BUTTON_CORNER_RADIUS_DP = 18
        const val BUTTON_SPACING_DP = 4
        const val BUTTON_TEXT_SIZE_SP = 10f

        const val DISABLED_ALPHA = 0.35f
    }
}
