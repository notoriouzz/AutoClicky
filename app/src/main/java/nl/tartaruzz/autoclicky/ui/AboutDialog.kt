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

package nl.tartaruzz.autoclicky.ui

import android.content.Context
import android.content.pm.PackageManager

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

import nl.tartaruzz.autoclicky.R

/**
 * "About" dialog, showing the license of the application and the credits of the vendored upstream project.
 *
 * The full GNU GPL v3.0 text is displayed straight from the resource bundled in the APK, so that the license notice
 * travels with every distributed build.
 */
@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val licenseText = remember { context.readRawText(R.raw.gpl_3_0) }
    val versionName = remember { context.appVersionName() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About AutoClicky") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (versionName.isEmpty()) "AutoClicky" else "AutoClicky $versionName",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "Copyright (C) 2026 AutoClicky contributors.\n" +
                        "Licensed under the GNU General Public License v3.0 or later.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "The clicking engine is based on Smart AutoClicker (Klick'r) by Kevin Buzeau and " +
                        "contributors, also licensed under the GPL-3.0-or-later:\n" +
                        "https://github.com/Nain57/Smart-AutoClicker",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "This program is distributed in the hope that it will be useful, but WITHOUT ANY " +
                        "WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A " +
                        "PARTICULAR PURPOSE. Anyone you distribute this application to is entitled to the " +
                        "complete corresponding source code.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "GNU General Public License v3.0",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = licenseText,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

private fun Context.readRawText(resourceId: Int): String =
    resources.openRawResource(resourceId).bufferedReader().use { it.readText() }

private fun Context.appVersionName(): String = runCatching {
    packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName
}.getOrNull().orEmpty()
