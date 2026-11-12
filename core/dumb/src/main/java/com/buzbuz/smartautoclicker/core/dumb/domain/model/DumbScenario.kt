/*
 * Copyright (C) 2023 Kevin Buzeau
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
package com.buzbuz.smartautoclicker.core.dumb.domain.model

import com.buzbuz.smartautoclicker.core.base.ScenarioStats
import com.buzbuz.smartautoclicker.core.base.interfaces.Identifiable
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier

data class DumbScenario(
    override val id: Identifier,
    val name: String,
    val dumbActions: List<DumbAction> = emptyList(),
    override val repeatCount: Int,
    override val isRepeatInfinite: Boolean,
    val maxDurationMin: Int,
    val isDurationInfinite: Boolean,
    val randomize: Boolean,
    /**
     * True while the user pinned the recording, so it is protected from the automatic cleanup of the unpinned
     * recordings (which keeps at most five of them).
     */
    val pinned: Boolean = false,
    /** When the recording was created, in epoch milliseconds. Used to order the recordings by their actual date. */
    val createdAtMs: Long = 0L,
    val stats: ScenarioStats? = null,
) : Identifiable, Repeatable {

    fun isValid(): Boolean =
        name.isNotEmpty() && dumbActions.isNotEmpty() && dumbActions.all { action -> action.isValid() }
}

const val DUMB_SCENARIO_MIN_DURATION_MINUTES = 1
const val DUMB_SCENARIO_MAX_DURATION_MINUTES = 1440
