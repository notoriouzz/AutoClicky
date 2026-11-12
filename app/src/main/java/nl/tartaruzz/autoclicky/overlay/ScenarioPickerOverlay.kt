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
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbScenario

import kotlin.math.roundToInt

/**
 * Lets the user pick a stored template and play it, above the app they are currently in.
 *
 * Displayed by the floating panel, it lists every template with its number of actions and a play button, so a macro can
 * be chosen and started without opening AutoClicky. The rows are dimmed while a template is already playing, because the
 * engine only runs one scenario at a time.
 */
class ScenarioPickerOverlay(
    private val service: AccessibilityService,
    private val scenarios: List<DumbScenario>,
    private val isScenarioEnabled: Boolean,
    private val onScenarioSelected: (scenario: DumbScenario) -> Unit,
    private val onDismissed: () -> Unit,
) {

    private val windowManager: WindowManager? = service.getSystemService(WindowManager::class.java)

    private var rootView: FrameLayout? = null

    /** The scrolling list, to re-apply its height cap after a rotation. */
    private var listView: ScrollView? = null

    /** Attach the picker. Returns false when the window manager refused the window. */
    fun show(): Boolean {
        val windowManager = windowManager ?: return false
        if (rootView != null) return true

        val root = buildRoot()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
        }

        val added = try {
            windowManager.addView(root, params)
            true
        } catch (_: IllegalStateException) {
            false
        } catch (_: IllegalArgumentException) {
            false
        }

        if (!added) return false

        rootView = root
        return true
    }

    /** Detach the picker from the window manager. */
    fun dismiss() {
        val windowManager = windowManager ?: return
        val root = rootView ?: return

        rootView = null

        try {
            windowManager.removeView(root)
        } catch (_: IllegalArgumentException) {
            // The view was already removed.
        }
    }

    private fun dismissAndNotify() {
        dismiss()
        onDismissed()
    }

    private fun buildRoot(): FrameLayout {
        val root = FrameLayout(service).apply {
            setBackgroundColor(SCRIM_COLOR)
            isClickable = true
            setOnClickListener { dismissAndNotify() } // A touch outside of the card closes the picker.
        }

        val card = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(toPx(16), toPx(14), toPx(16), toPx(14))
            background = roundedBackground(CARD_COLOR, 20)
            minimumWidth = toPx(300)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.CENTER }
            isClickable = true // Keeps the touches made on the card from closing it.
        }

        card.addView(buildHeader())
        card.addView(buildList())

        root.addView(card)
        return root
    }

    private fun buildHeader(): LinearLayout =
        LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            addView(
                TextView(service).apply {
                    text = "Templates"
                    setTextColor(Color.WHITE)
                    textSize = TITLE_TEXT_SIZE_SP
                    typeface = Typeface.DEFAULT_BOLD
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
            )
            addView(
                TextView(service).apply {
                    text = "X"
                    setTextColor(Color.WHITE)
                    textSize = CLOSE_TEXT_SIZE_SP
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    contentDescription = "Close the template list"
                    background = roundedBackground(CLOSE_COLOR, 16)
                    isClickable = true
                    layoutParams = LinearLayout.LayoutParams(toPx(32), toPx(32))
                    setOnClickListener { dismissAndNotify() }
                }
            )
        }

    private fun buildList(): ScrollView {
        val container = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }

        if (scenarios.isEmpty()) {
            container.addView(
                TextView(service).apply {
                    text = "No template yet, record one first."
                    setTextColor(MUTED_TEXT_COLOR)
                    textSize = ROW_SUBTITLE_TEXT_SIZE_SP
                    setPadding(0, toPx(10), 0, toPx(10))
                }
            )
        } else {
            scenarios.forEach { scenario -> container.addView(buildRow(scenario)) }
        }

        return ScrollView(service).apply {
            isVerticalScrollBarEnabled = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = toPx(12) }
            addView(container)

            // Keep the list shorter than the screen, whatever the number of templates is.
            post { capListHeight(this) }
        }.also { listView = it }
    }

    /** Re-apply the height cap after a rotation, the previous one was computed for the other orientation. */
    fun onScreenRotated() {
        val list = listView ?: return
        list.post { capListHeight(list) }
    }

    private fun capListHeight(list: ScrollView) {
        val container = list.getChildAt(0) ?: return
        val maxHeight = (service.resources.displayMetrics.heightPixels * MAX_HEIGHT_RATIO).toInt()

        if (container.height > maxHeight) {
            list.layoutParams = list.layoutParams.apply { height = maxHeight }
            list.requestLayout()
        }
    }

    private fun buildRow(scenario: DumbScenario): LinearLayout {
        val row = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(toPx(12), toPx(10), toPx(8), toPx(10))
            background = roundedBackground(ROW_COLOR, 14)
            alpha = if (isScenarioEnabled) 1f else DISABLED_ALPHA
            isClickable = isScenarioEnabled
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = toPx(6) }

            if (isScenarioEnabled) {
                setOnClickListener { onScenarioSelected(scenario) }
            }
        }

        row.addView(
            LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                addView(
                    TextView(service).apply {
                        text = scenario.name
                        setTextColor(Color.WHITE)
                        textSize = ROW_TITLE_TEXT_SIZE_SP
                        isSingleLine = true
                        ellipsize = TextUtils.TruncateAt.END
                    }
                )
                addView(
                    TextView(service).apply {
                        text = "${scenario.dumbActions.size} action(s)"
                        setTextColor(MUTED_TEXT_COLOR)
                        textSize = ROW_SUBTITLE_TEXT_SIZE_SP
                    }
                )
            }
        )

        row.addView(
            TextView(service).apply {
                text = "\u25B6"
                setTextColor(Color.WHITE)
                textSize = PLAY_TEXT_SIZE_SP
                gravity = Gravity.CENTER
                includeFontPadding = false
                contentDescription = "Play ${scenario.name}"
                background = roundedBackground(PLAY_COLOR, 18)
                isClickable = true
                layoutParams = LinearLayout.LayoutParams(toPx(36), toPx(36)).apply { marginStart = toPx(8) }
                setOnClickListener {
                    if (isScenarioEnabled) onScenarioSelected(scenario)
                }
            }
        )

        return row
    }

    private fun roundedBackground(color: Int, cornerRadiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = toPx(cornerRadiusDp).toFloat()
            setColor(color)
        }

    private fun toPx(valueDp: Int): Int = (valueDp * service.resources.displayMetrics.density).roundToInt()

    private companion object {
        val SCRIM_COLOR: Int = Color.parseColor("#99000000")
        val CARD_COLOR: Int = Color.parseColor("#FF25252A")
        val ROW_COLOR: Int = Color.parseColor("#1AFFFFFF")
        val CLOSE_COLOR: Int = Color.parseColor("#33FFFFFF")
        val PLAY_COLOR: Int = Color.parseColor("#FF2E7D32")
        val MUTED_TEXT_COLOR: Int = Color.parseColor("#B3FFFFFF")

        const val MAX_HEIGHT_RATIO = 0.55f
        const val DISABLED_ALPHA = 0.4f
        const val TITLE_TEXT_SIZE_SP = 17f
        const val CLOSE_TEXT_SIZE_SP = 12f
        const val ROW_TITLE_TEXT_SIZE_SP = 15f
        const val ROW_SUBTITLE_TEXT_SIZE_SP = 12f
        const val PLAY_TEXT_SIZE_SP = 14f
    }
}
