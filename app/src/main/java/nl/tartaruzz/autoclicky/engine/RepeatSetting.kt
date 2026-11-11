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

package nl.tartaruzz.autoclicky.engine

import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbScenario
import com.buzbuz.smartautoclicker.core.dumb.domain.model.REPEAT_COUNT_MAX_VALUE
import com.buzbuz.smartautoclicker.core.dumb.domain.model.REPEAT_COUNT_MIN_VALUE
import com.buzbuz.smartautoclicker.core.dumb.domain.model.RepeatableWithDelay

/**
 * How many times a template must be played, and how long the engine waits between two passes.
 *
 * [isRepeatInfinite] runs the template until it is stopped, either from the panel or by touching the screen.
 * [loopDelayMs] is the idle time inserted after the last action of a pass, before the next one starts.
 */
data class RepeatSetting(
    val repeatCount: Int = 1,
    val isRepeatInfinite: Boolean = false,
    val loopDelayMs: Long = 0L,
) {

    /** True when the template is played once and the engine stops by itself. */
    val isSingleRun: Boolean get() = !isRepeatInfinite && repeatCount <= 1

    /** Short label for the panel button and the cards. */
    val label: String get() = if (isRepeatInfinite) INFINITE_LABEL else "${repeatCount}×"

    /** Apply the bounds of the engine model, so an invalid setting can never reach the scenario. */
    fun sanitized(): RepeatSetting = copy(
        repeatCount = repeatCount.coerceIn(REPEAT_COUNT_MIN_VALUE, REPEAT_COUNT_MAX_VALUE),
        loopDelayMs = loopDelayMs.coerceAtLeast(0L),
    )

    companion object {
        const val INFINITE_LABEL = "∞"

        /** One pass, no loop delay: what the recorder produces by default. */
        val SINGLE = RepeatSetting()

        /** The repeat choices offered by the cards. */
        val COUNT_CHOICES = listOf(1, 3, 5, 10, 50)

        /** The idle times offered by the cards, in milliseconds. */
        val LOOP_DELAY_CHOICES = listOf(0L, 500L, 1_000L, 3_000L, 10_000L)
    }
}

/**
 * The repeat settings stored in this template.
 *
 * The idle time between two passes is stored on the last action of the scenario: the engine waits the `repeatDelayMs`
 * of an action after executing it, so the last one is also the pause before the next pass.
 */
fun DumbScenario.toRepeatSetting(): RepeatSetting {
    val lastAction = dumbActions.lastOrNull() as? RepeatableWithDelay

    return RepeatSetting(
        repeatCount = repeatCount,
        isRepeatInfinite = isRepeatInfinite,
        loopDelayMs = if (isRepeatInfinite || repeatCount > 1) lastAction?.repeatDelayMs ?: 0L else 0L,
    ).sanitized()
}
