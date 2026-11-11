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
import android.text.InputType
import android.view.Gravity
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

import kotlin.math.roundToInt

/**
 * Asks for the name of the recording that was just captured, above the app the user is currently in.
 *
 * This is the floating counterpart of the "Save recording" dialog of the activity: it is displayed as soon as a capture
 * ends, so a recording started from the panel can be saved without going back to AutoClicky. The window is a focusable
 * [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY] window, which is what allows the soft keyboard to be used
 * inside an accessibility overlay.
 *
 * Saving with the prefilled name requires a single tap: the text field is only there to change it.
 */
class SaveRecordingOverlay(
    private val service: AccessibilityService,
    private val actionCount: Int,
    private val defaultName: String,
    private val onSave: (name: String) -> Unit,
    private val onCancel: () -> Unit,
) {

    private val windowManager: WindowManager? = service.getSystemService(WindowManager::class.java)

    private var rootView: FrameLayout? = null
    private var nameField: EditText? = null

    /** Attach the card. Returns false when the window manager refused the window. */
    fun show(): Boolean {
        val windowManager = windowManager ?: return false
        if (rootView != null) return true

        val root = buildRoot()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
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
        nameField = null

        try {
            windowManager.removeView(root)
        } catch (_: IllegalArgumentException) {
            // The view was already removed.
        }
    }

    private fun buildRoot(): FrameLayout {
        val root = FrameLayout(service).apply {
            setBackgroundColor(SCRIM_COLOR)
            isClickable = true // Swallows the touches made outside of the card.
        }

        val card = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(toPx(20), toPx(18), toPx(20), toPx(14))
            background = roundedBackground(CARD_COLOR, 20)
            minimumWidth = toPx(280)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.CENTER }
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
                text = "$actionCount action(s)"
                setTextColor(MUTED_TEXT_COLOR)
                textSize = SUBTITLE_TEXT_SIZE_SP
            }
        )

        card.addView(buildNameField())

        card.addView(buildButtons())

        root.addView(card)
        return root
    }

    private fun buildNameField(): EditText =
        EditText(service).apply {
            setText(defaultName)
            setSelection(defaultName.length)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_DONE
            hint = "Template name"
            setTextColor(Color.WHITE)
            setHintTextColor(MUTED_TEXT_COLOR)
            textSize = FIELD_TEXT_SIZE_SP
            background = roundedBackground(FIELD_COLOR, 12)
            setPadding(toPx(14), toPx(10), toPx(14), toPx(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = toPx(14) }
        }.also { field -> nameField = field }

    private fun buildButtons(): LinearLayout =
        LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = toPx(16) }

            addView(
                actionButton(
                    label = "Cancel",
                    backgroundColor = Color.TRANSPARENT,
                    textColor = MUTED_TEXT_COLOR,
                    onClick = { onCancel() },
                )
            )
            addView(
                actionButton(
                    label = "Save",
                    backgroundColor = SAVE_COLOR,
                    textColor = Color.WHITE,
                    onClick = { onSave(currentName()) },
                )
            )
        }

    private fun actionButton(
        label: String,
        backgroundColor: Int,
        textColor: Int,
        onClick: () -> Unit,
    ): TextView = TextView(service).apply {
        text = label
        setTextColor(textColor)
        textSize = BUTTON_TEXT_SIZE_SP
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        includeFontPadding = false
        setPadding(toPx(18), toPx(10), toPx(18), toPx(10))
        if (backgroundColor != Color.TRANSPARENT) {
            background = roundedBackground(backgroundColor, 14)
        }
        isClickable = true
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { marginStart = toPx(8) }
        setOnClickListener { onClick() }
    }

    private fun currentName(): String = nameField?.text?.toString().orEmpty()

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
        val FIELD_COLOR: Int = Color.parseColor("#33FFFFFF")
        val SAVE_COLOR: Int = Color.parseColor("#FF3949AB")
        val MUTED_TEXT_COLOR: Int = Color.parseColor("#B3FFFFFF")

        const val TITLE_TEXT_SIZE_SP = 18f
        const val SUBTITLE_TEXT_SIZE_SP = 12f
        const val FIELD_TEXT_SIZE_SP = 15f
        const val BUTTON_TEXT_SIZE_SP = 14f
    }
}
