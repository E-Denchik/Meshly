/*
 * Copyright (C) 2026 The Meshly Project Authors
 *
 * This file is part of Meshly, a decentralized peer-to-peer messenger
 * built on top of GNU Jami's core engine (libjami).
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

package org.meshly.app.daemon

/**
 * `DataTransferCallback.dataTransferEvent`'s `eventCode`, and `fileTransferInfo`/
 * `cancelDataTransfer`'s `DataTransferError` return (SWIG-`%apply`'d to plain `uint32_t`).
 * Verbatim from `libjami::DataTransferEventCode` (src/jami/datatransfer_interface.h) — note the
 * daemon really does start this enum at `invalid = 0`, then `created` at 1, not "created" at 0.
 *
 * `wireValue`/`fromWireValue` are `Long`, not `Int`: SWIG's documented default Java typemap
 * widens unsigned C++ types to the next larger *signed* Java primitive so the full unsigned range
 * fits (`unsigned short` -> `int`, `unsigned int` -> `long`, `unsigned long`/`uint64_t` ->
 * `BigInteger`) -- this project's own `%apply int64_t { uint64_t }` override in jni_interface.i,
 * with the comment "Avoid uint64_t to be converted to BigInteger", is direct corroborating
 * evidence it follows that exact convention. There's no equivalent override for plain `uint32_t`
 * anywhere in the .i files, so it should fall through to the `long` default.
 */
enum class RealDataTransferEventCode(val wireValue: Long) {
    INVALID(0),
    CREATED(1),
    UNSUPPORTED(2),
    WAIT_PEER_ACCEPTANCE(3),
    WAIT_HOST_ACCEPTANCE(4),
    ONGOING(5),
    FINISHED(6),
    CLOSED_BY_HOST(7),
    CLOSED_BY_PEER(8),
    INVALID_PATHNAME(9),
    UNJOINABLE_PEER(10),
    TIMEOUT_EXPIRED(11);

    companion object {
        fun fromWireValue(value: Long): RealDataTransferEventCode =
            entries.firstOrNull { it.wireValue == value } ?: INVALID
    }
}

/**
 * `libjami::DataTransferError` (src/jami/datatransfer_interface.h) — `cancelDataTransfer`'s and
 * `fileTransferInfo`'s return value. `Long`, not `Int` -- see [RealDataTransferEventCode]'s doc.
 */
enum class RealDataTransferError(val wireValue: Long) {
    SUCCESS(0),
    UNKNOWN(1),
    IO(2),
    INVALID_ARGUMENT(3);

    companion object {
        fun fromWireValue(value: Long): RealDataTransferError =
            entries.firstOrNull { it.wireValue == value } ?: UNKNOWN
    }
}

/**
 * Result of [RealJamiBridge.fileTransferInfo]. libjami returns these three values via SWIG
 * `OUTPUT` typemapped parameters (datatransfer.i's `%apply std::string& OUTPUT { path_out }` /
 * `%apply int64_t& OUTPUT { total_out }` / `{ progress_out }`) rather than a struct — that
 * typemap pattern generates single-element-array "out parameters" on the Java side (`String[]`
 * for the path, explicitly shown in the .i file's own `std::string& OUTPUT` typemap block; `long[]`
 * for the two `int64_t` ones is SWIG's standard, well-documented `TYPE& OUTPUT` idiom for a
 * primitive out-parameter, and `int64_t` is independently confirmed `long` here via the
 * `%apply int64_t { uint64_t }` chain used elsewhere -- reasonably confident, if still not
 * confirmed against an actual generated build), which is why [RealJamiBridge.fileTransferInfo]
 * has to allocate and pass those arrays in rather than just reading a return value.
 */
data class RealFileTransferInfo(val error: RealDataTransferError, val path: String, val totalBytes: Long, val progressBytes: Long)
