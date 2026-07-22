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
 * `DataTransferCallback.dataTransferEvent`'s `eventCode` int, and `fileTransferInfo`/
 * `cancelDataTransfer`'s `DataTransferError` return (SWIG-`%apply`'d to plain `uint32_t`/`int`).
 * Verbatim from `libjami::DataTransferEventCode` (src/jami/datatransfer_interface.h) — note the
 * daemon really does start this enum at `invalid = 0`, then `created` at 1, not "created" at 0.
 */
enum class RealDataTransferEventCode(val wireValue: Int) {
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
        fun fromWireValue(value: Int): RealDataTransferEventCode =
            entries.firstOrNull { it.wireValue == value } ?: INVALID
    }
}

/** `libjami::DataTransferError` (src/jami/datatransfer_interface.h) — `cancelDataTransfer`'s and
 *  `fileTransferInfo`'s return value. */
enum class RealDataTransferError(val wireValue: Int) {
    SUCCESS(0),
    UNKNOWN(1),
    IO(2),
    INVALID_ARGUMENT(3);

    companion object {
        fun fromWireValue(value: Int): RealDataTransferError =
            entries.firstOrNull { it.wireValue == value } ?: UNKNOWN
    }
}

/**
 * Result of [RealJamiBridge.fileTransferInfo]. libjami returns these three values via SWIG
 * `OUTPUT` typemapped parameters (datatransfer.i's `%apply std::string& OUTPUT { path_out }` /
 * `%apply int64_t& OUTPUT { total_out }` / `{ progress_out }`) rather than a struct — that
 * typemap pattern generates single-element-array "out parameters" on the Java side (`String[]`
 * for the path, presumably `long[]` for the two int64_t ones, mirroring the `std::string&
 * OUTPUT` typemap shown explicitly in the .i file), which is why [RealJamiBridge.fileTransferInfo]
 * has to allocate and pass those arrays in rather than just reading a return value. The exact
 * array element type for the int64_t OUTPUT params (`long[]` vs `Long[]`) isn't confirmed against
 * a real generated build.
 */
data class RealFileTransferInfo(val error: RealDataTransferError, val path: String, val totalBytes: Long, val progressBytes: Long)
