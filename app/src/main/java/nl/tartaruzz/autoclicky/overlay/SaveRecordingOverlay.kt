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
import android.widget.TextView

import kotlin.math.roundToInt

/**
 * Offers to save the recording that was just captured, above the app the user is in.
 *
 * This is the floating counterpart of the "Save recording" dialog of the activity: it is displayed as soon as a capture
 * ends, so a recording started from the panel can be saved without going back to AutoClicky.
 *
 * The card deliberately has no text field: an editable text opens the keyboard inside the overlay window, which on
 * several devices leaves the buttons unreachable and the card blocking the whole screen. It shows the proposed name with
 * a large SAVE and DISCARD button anchored above the navigation bar; the template can be renamed in the activity.
 */
class SaveRecordingOverlay(
    private val service: AccessibilityService,
    private val summary: String,
    private val recordingName: String,
    private val onSave: () -> Unit,
    private val onCancel: () -> Unit,
) {

    private val windowManager: WindowManager? = service.getSystemService(WindowManager::class.java)

    private var rootView: FrameLayout? = null

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
            setOnClickListener { onCancel() } // A touch outside the card just closes it.
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
        }

        card.addView(
            TextView(service).apply {
                text = "Save recording"
                setTextColor(Color.WHITE)
                textSize = TITLE_TEXT_SIZE_SP
                typeface = Typeface.DEFAULT_BOLD
            }
        )

        card.addView(
            TextView(service).apply {
                text = summary
                setTextColor(MUTED_TEXT_COLOR)
                textSize = SUBTITLE_TEXT_SIZE_SP
            }
        )

        card.addView(
            TextView(service).apply {
                text = recordingName
                setTextColor(Color.WHITE)
                textSize = NAME_TEXT_SIZE_SP
                isSingleLine = true
                ellipsize = TextUtils.TruncateAt.END
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
                    label = "DISCARD",
                    backgroundColor = DISCARD_COLOR,
                    onClick = { onCancel() },
                )
            )
            addView(
                actionButton(
                    label = "SAVE",
                    backgroundColor = SAVE_COLOR,
                    onClick = { onSave() },
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
        val DISCARD_COLOR: Int = Color.parseColor("#FF3A3A40")
        val SAVE_COLOR: Int = Color.parseColor("#FF2E7D32")
        val MUTED_TEXT_COLOR: Int = Color.parseColor("#B3FFFFFF")

        const val CARD_WIDTH_RATIO = 0.9f
        const val CARD_BOTTOM_MARGIN_DP = 56
        const val BUTTON_HEIGHT_DP = 56
        const val BUTTON_SPACING_DP = 12

        const val TITLE_TEXT_SIZE_SP = 18f
        const val SUBTITLE_TEXT_SIZE_SP = 12f
        const val NAME_TEXT_SIZE_SP = 14f
        const val BUTTON_TEXT_SIZE_SP = 15f
    }
}
