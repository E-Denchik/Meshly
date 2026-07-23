/*
 * Copyright (C) 2026 The Meshly Project Authors
 *
 * This file is part of Meshly, a decentralized peer-to-peer messenger
 * built on top of Tox (c-toxcore + ToxAV).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

// Phase 2 scaffolding for the real c-toxcore/ToxAV native engine. See
// /PHASE2_BUILD_TOX.md before touching this file. Unlike the jami-daemon
// scaffold this replaces, c-toxcore's own dependency footprint is small
// (libsodium for toxcore, + opus/libvpx for toxav -- no ~30-library contrib
// tree), so the realistic resource budget here is on the order of low
// single-digit GB of disk and well under an hour of CPU time per ABI, not
// the 30-50GB/hours the jami-daemon version needed. See PHASE2_BUILD_TOX.md
// for the full breakdown.
//
// This module is intentionally NOT included in settings.gradle.kts yet, and
// :app does not depend on it, so the Phase 1 mock build (./gradlew assembleDebug)
// is completely unaffected by anything in here.
//
// STRUCTURAL DIFFERENCE FROM THE REMOVED :daemon (JAMI) MODULE: jami-daemon
// generates its own JNI bindings via SWIG, so the old module pointed
// externalNativeBuild.cmake.path directly at the submodule's own
// CMakeLists.txt. c-toxcore has NO SWIG/binding-generator step at all -- its
// CMakeLists.txt (native/upstream/c-toxcore/CMakeLists.txt) only knows how to
// build the plain C library, nothing about JNI or Android. So this module
// ships its own CMakeLists.txt (daemon-tox/CMakeLists.txt) that
// add_subdirectory()s the c-toxcore submodule and links a small
// hand-written JNI wrapper (src/main/cpp/tox_jni.c) against it. There is no
// generated-sources sourceSet wiring here (contrast the removed :daemon
// module's `jamiJniPackageDir` source set) because nothing generates Kotlin
// or Java sources on the Tox side -- ToxNative.kt's `external fun`
// declarations are hand-written to match tox_jni.c's JNI entry points.

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "org.meshly.app.daemontox"
    // compileSdk matches app/build.gradle.kts's compileSdk = 35 (confirmed).
    compileSdk = 35
    // app/build.gradle.kts does not pin an ndkVersion at all (confirmed by
    // reading it), so there is nothing to literally match here. This keeps
    // the same NDK version the removed :daemon (Jami) module pinned, for
    // continuity -- not independently re-verified against this machine's
    // installed NDK in this pass.
    ndkVersion = "27.2.12479018"

    defaultConfig {
        minSdk = 24

        // Start with a single ABI while bringing the native build up, same
        // reasoning as the removed :daemon module -- add the others back
        // once arm64-v8a is confirmed working end to end.
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
}
