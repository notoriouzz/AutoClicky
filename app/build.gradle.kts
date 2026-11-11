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

import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.google.dagger.hilt)
}

/*
 * Release signing.
 *
 * The keystore and its credentials are never part of the repository. They are read from the git-ignored
 * `keystore.properties` at the repository root, or from the AUTOCLICKY_KEYSTORE* environment variables:
 *
 *   storeFile=...        AUTOCLICKY_KEYSTORE
 *   storePassword=...    AUTOCLICKY_KEYSTORE_PASSWORD
 *   keyAlias=...         AUTOCLICKY_KEY_ALIAS
 *   keyPassword=...      AUTOCLICKY_KEY_PASSWORD
 *
 * Without them the release build is produced unsigned (app-release-unsigned.apk), which cannot be installed on a device.
 */
val signingProperties = Properties().apply {
    val propertiesFile = rootProject.file("keystore.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use { load(it) }
    }
}

fun signingValue(propertyName: String, environmentName: String): String? =
    (signingProperties.getProperty(propertyName) ?: System.getenv(environmentName))?.takeIf { it.isNotBlank() }

val releaseKeystoreFile = signingValue("storeFile", "AUTOCLICKY_KEYSTORE")?.let { file(it) }
val releaseKeystorePassword = signingValue("storePassword", "AUTOCLICKY_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "AUTOCLICKY_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "AUTOCLICKY_KEY_PASSWORD")
val hasReleaseSigning = releaseKeystoreFile != null &&
    releaseKeystorePassword != null &&
    releaseKeyAlias != null &&
    releaseKeyPassword != null

android {
    namespace = "nl.tartaruzz.autoclicky"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "nl.tartaruzz.autoclicky"
        minSdk = 36
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                // Keep the standard R8 configuration: letting R8 repackage the Kotlin/AndroidX libraries
                // (packageScope) made the app crash at startup with an IllegalAccessError on kotlin.text internals.
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Vendored Smart AutoClicker (Klick'r) clicking engine, see THIRD_PARTY_NOTICES.md
    implementation(project(":core:dumb"))
    implementation(project(":core:common:actions"))
    implementation(project(":core:common:base"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.google.dagger.hilt)
    ksp(libs.google.dagger.hilt.compiler)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}