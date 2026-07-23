cmake_minimum_required(VERSION 3.22.1)
project(toxcore-jni)

set(TOXCORE_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../native/upstream/c-toxcore)

# c-toxcore's cmake/Dependencies.cmake finds libsodium/opus/vpx via
# find_package(PkgConfig REQUIRED) + pkg_search_module(...) (confirmed by reading that file).
# Point pkg-config at this module's own cross-compiled deps/<ABI>/lib/pkgconfig staging tree
# (built per PHASE2_BUILD_TOX.md's steps: libsodium via its own dist-build/android-*.sh,
# opus/vpx via the NDK CMake/clang toolchain directly) instead of relying on the invoking
# shell's environment -- set(ENV{...}) here makes this reproducible whether CMake is invoked
# standalone or through Gradle's externalNativeBuild, which doesn't otherwise let you set
# subprocess environment variables via cmake.arguments (only -D cache variables).
set(ENV{PKG_CONFIG_LIBDIR} "${CMAKE_CURRENT_SOURCE_DIR}/deps/${ANDROID_ABI}/lib/pkgconfig")
set(ENV{PKG_CONFIG_PATH} "")
set(ENV{PKG_CONFIG_SYSROOT_DIR} "")

# c-toxcore's own top-level CMakeLists.txt (TOXCORE_DIR/CMakeLists.txt) builds
# ONE merged library containing toxcore + toxav + toxencryptsave together
# (confirmed: c-toxcore/INSTALL.md's component table says "at the moment,
# when building the libraries, they are all merged into a single `toxcore`
# library"; CMakeLists.txt folds toxav/*.c into the same `toxcore_SOURCES`
# list under `if(BUILD_TOXAV)`, lines 424-457) -- there is no separate
# libtoxav target to link against in addition.
add_subdirectory(${TOXCORE_DIR} ${CMAKE_CURRENT_BINARY_DIR}/c-toxcore)

add_library(toxcore-jni SHARED
    src/main/cpp/tox_jni.c
)

# toxcore_static's own headers live at TOXCORE_DIR/{toxcore,toxav}/*.h (source-tree layout,
# not an installed "tox/" prefix) -- tox_jni.c includes them as "toxcore/tox.h" and
# "toxav/toxav.h", so TOXCORE_DIR itself must be on this target's include path. toxcore_static
# does declare these as part of its own target_include_directories, but PRIVATE visibility
# (confirmed: TOXCORE_DIR/CMakeLists.txt target_link_libraries(toxcore_static PRIVATE ...))
# doesn't reliably propagate to consumers, so it's added explicitly here too.
target_include_directories(toxcore-jni PRIVATE ${TOXCORE_DIR})

find_library(log-lib log)

# Confirmed from TOXCORE_DIR/CMakeLists.txt (lines 519-537): the actual CMake
# *target* names are "toxcore_shared" (if ENABLE_SHARED, the default per
# cmake/ModulePackage.cmake line 1) and "toxcore_static" (if ENABLE_STATIC,
# also default ON) -- NOT a target literally named "toxcore". Both targets
# set OUTPUT_NAME to "toxcore" (line 521 / 531), so the *file* is
# libtoxcore.so / libtoxcore.a, but the CMake target you link against in
# target_link_libraries() must be "toxcore_shared" or "toxcore_static". For
# an Android .so, link the static variant of c-toxcore into this JNI shared
# object so only one .so needs to be packaged/loaded (avoids having to also
# ship a separate libtoxcore.so and get its load order right) -- hence
# toxcore_static below. This has NOT been build-tested against the real
# Android NDK toolchain yet; if ENABLE_SHARED/ENABLE_STATIC options need to
# be forced (e.g. -DENABLE_STATIC=ON -DENABLE_SHARED=OFF) that should be
# added to defaultConfig.externalNativeBuild.cmake.arguments in
# daemon-tox/build.gradle.kts once this is actually built.
#
# Also note (not yet resolved, see PHASE2_BUILD_TOX.md "Still open"):
# c-toxcore's CMakeLists.txt resolves libsodium (required) and opus/libvpx
# (required for BUILD_TOXAV, default ON) via pkg-config / find_package
# (cmake/Dependencies.cmake) -- it does NOT vendor or FetchContent them. For
# an Android cross-compile, those three libraries need to be made visible to
# CMake's find_package/pkg-config machinery for the target ABI (e.g. via a
# prebuilt-for-Android libsodium/opus/libvpx with a CMake package config, or
# a PKG_CONFIG_PATH pointed at Android-target .pc files) BEFORE this
# add_subdirectory() call will configure cleanly. That step is not scaffolded
# here yet.
target_link_libraries(toxcore-jni
    toxcore_static
    ${log-lib}
)
