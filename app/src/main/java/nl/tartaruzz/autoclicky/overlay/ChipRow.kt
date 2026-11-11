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
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

import kotlin.math.roundToInt

/**
 * A row of single choice labels, used by the cards that need a small setting (repeat count, pause between passes).
 *
 * Only one label is selected at a time, and the selection is drawn on the labels themselves: a plain [TextView] keeps
 * the cards free of any Android widget theme, like the rest of the overlays.
 */
internal fun buildChipRow(
    service: AccessibilityService,
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
): LinearLayout {
    var selected = selectedIndex

    val row = LinearLayout(service).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    val chips = labels.map { label ->
        TextView(service).apply {
            text = label
            textSize = CHIP_TEXT_SIZE_SP
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            isClickable = true
            layoutParams = LinearLayout.LayoutParams(0, chipHeightPx(service), 1f).apply {
                marginStart = chipMarginPx(service)
                marginEnd = chipMarginPx(service)
            }
        }
    }

    fun applySelection() {
        chips.forEachIndexed { index, chip ->
            val isSelected = index == selected
            chip.setTextColor(if (isSelected) Color.WHITE else MUTED_TEXT_COLOR)
            chip.background = chipBackground(
                service = service,
                color = if (isSelected) SELECTED_COLOR else UNSELECTED_COLOR,
            )
        }
    }

    chips.forEachIndexed { index, chip ->
        chip.setOnClickListener {
            if (selected == index) return@setOnClickListener
            selected = index
            applySelection()
            onSelected(index)
        }
        row.addView(chip)
    }

    applySelection()
    return row
}

private fun chipBackground(service: AccessibilityService, color: Int): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = (12 * service.resources.displayMetrics.density)
        setColor(color)
    }

private fun chipHeightPx(service: AccessibilityService): Int =
    (CHIP_HEIGHT_DP * service.resources.displayMetrics.density).roundToInt()

private fun chipMarginPx(service: AccessibilityService): Int =
    (CHIP_SPACING_DP * service.resources.displayMetrics.density).roundToInt()

private val SELECTED_COLOR: Int = Color.parseColor("#FF2E7D32")
private val UNSELECTED_COLOR: Int = Color.parseColor("#1AFFFFFF")
private val MUTED_TEXT_COLOR: Int = Color.parseColor("#B3FFFFFF")

private const val CHIP_TEXT_SIZE_SP = 12f
private const val CHIP_HEIGHT_DP = 34
private const val CHIP_SPACING_DP = 4
