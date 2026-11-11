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
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

import nl.tartaruzz.autoclicky.engine.RepeatSetting

import kotlin.math.roundToInt

/**
 * Lets the user choose how many times the last template must be played, then starts it.
 *
 * Opened from the "×N" button of the floating panel. The endless choice runs the template until the STOP button is
 * pressed or until the screen is touched, which is handled by [TouchStopController].
 */
class RepeatOverlay(
    private val service: AccessibilityService,
    private val templateName: String,
    private val initialRepeat: RepeatSetting,
    private val onPlay: (RepeatSetting) -> Unit,
    private val onCancel: () -> Unit,
) {

    private val windowManager: WindowManager? = service.getSystemService(WindowManager::class.java)

    private var rootView: FrameLayout? = null

    private var repeatSetting: RepeatSetting = initialRepeat.sanitized()

    /** Attach the card. Returns false when the window manager refused the window. */
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

    /** Detach the card from the window manager. */
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

    private fun buildRoot(): FrameLayout {
        val root = FrameLayout(service).apply {
            setBackgroundColor(SCRIM_COLOR)
            isClickable = true
            setOnClickListener {
                Log.d(TAG, "Touch outside the card, closing it")
                onCancel()
            }
        }

        val card = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(toPx(20), toPx(18), toPx(20), toPx(18))
            background = roundedBackground(CARD_COLOR, 20)
            layoutParams = FrameLayout.LayoutParams(
                (service.resources.displayMetrics.widthPixels * CARD_WIDTH_RATIO).toInt(),
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = toPx(CARD_BOTTOM_MARGIN_DP)
            }
            isClickable = true
            setOnClickListener { Log.d(TAG, "Touch on the card itself, nothing to do") }
        }

        card.addView(
            TextView(service).apply {
                text = "Repeat"
                setTextColor(Color.WHITE)
                textSize = TITLE_TEXT_SIZE_SP
                typeface = Typeface.DEFAULT_BOLD
            }
        )

        card.addView(
            TextView(service).apply {
                text = templateName
                setTextColor(Color.WHITE)
                textSize = NAME_TEXT_SIZE_SP
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = toPx(4) }
            }
        )

        card.addView(sectionLabel("Repeat"))
        card.addView(
            buildChipRow(
                service = service,
                labels = REPEAT_LABELS,
                selectedIndex = REPEAT_LABELS.indexOf(repeatSetting.label).coerceAtLeast(0),
                onSelected = { index ->
                    val isInfinite = index == REPEAT_LABELS.lastIndex
                    repeatSetting = repeatSetting.copy(
                        repeatCount = if (isInfinite) repeatSetting.repeatCount else REPEAT_COUNTS[index],
                        isRepeatInfinite = isInfinite,
                    )
                },
            )
        )

        card.addView(sectionLabel("Pause between passes"))
        card.addView(
            buildChipRow(
                service = service,
                labels = LOOP_DELAY_LABELS,
                selectedIndex = LOOP_DELAYS.indexOf(repeatSetting.loopDelayMs).coerceAtLeast(0),
                onSelected = { index ->
                    repeatSetting = repeatSetting.copy(loopDelayMs = LOOP_DELAYS[index])
                },
            )
        )

        card.addView(
            TextView(service).apply {
                text = "Endless runs until STOP, or until you touch the screen"
                setTextColor(MUTED_TEXT_COLOR)
                textSize = HINT_TEXT_SIZE_SP
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = toPx(10) }
            }
        )

        card.addView(buildButtons())
        root.addView(card)
        return root
    }

    private fun buildButtons(): LinearLayout =
        LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = toPx(18) }

            addView(
                actionButton(
                    label = "CANCEL",
                    backgroundColor = CANCEL_COLOR,
                    onClick = { onCancel() },
                )
            )
            addView(
                actionButton(
                    label = "PLAY",
                    backgroundColor = PLAY_COLOR,
                    onClick = { onPlay(repeatSetting) },
                )
            )
        }

    private fun actionButton(
        label: String,
        backgroundColor: Int,
        onClick: () -> Unit,
    ): TextView = TextView(service).apply {
        text = label
        setTextColor(Color.WHITE)
        textSize = BUTTON_TEXT_SIZE_SP
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        includeFontPadding = false
        background = roundedBackground(backgroundColor, 16)
        isClickable = true
        layoutParams = LinearLayout.LayoutParams(0, toPx(BUTTON_HEIGHT_DP), 1f).apply {
            marginStart = toPx(BUTTON_SPACING_DP / 2)
            marginEnd = toPx(BUTTON_SPACING_DP / 2)
        }
        setOnClickListener { onClick() }
    }

    private fun sectionLabel(text: String): TextView =
        TextView(service).apply {
            this.text = text
            setTextColor(MUTED_TEXT_COLOR)
            textSize = SUBTITLE_TEXT_SIZE_SP
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = toPx(10) }
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
        val CANCEL_COLOR: Int = Color.parseColor("#FF3A3A40")
        val PLAY_COLOR: Int = Color.parseColor("#FF2E7D32")
        val MUTED_TEXT_COLOR: Int = Color.parseColor("#B3FFFFFF")

        val REPEAT_COUNTS: List<Int> = RepeatSetting.COUNT_CHOICES
        val REPEAT_LABELS: List<String> = REPEAT_COUNTS.map { count -> "${count}×" } + RepeatSetting.INFINITE_LABEL
        val LOOP_DELAYS: List<Long> = RepeatSetting.LOOP_DELAY_CHOICES
        val LOOP_DELAY_LABELS: List<String> = listOf("0s", "0.5s", "1s", "3s", "10s")

        const val CARD_WIDTH_RATIO = 0.9f
        const val CARD_BOTTOM_MARGIN_DP = 56
        const val BUTTON_HEIGHT_DP = 52
        const val BUTTON_SPACING_DP = 12

        const val TITLE_TEXT_SIZE_SP = 18f
        const val NAME_TEXT_SIZE_SP = 13f
        const val SUBTITLE_TEXT_SIZE_SP = 12f
        const val HINT_TEXT_SIZE_SP = 11f
        const val BUTTON_TEXT_SIZE_SP = 15f

        const val TAG = "RepeatOverlay"
    }
}
