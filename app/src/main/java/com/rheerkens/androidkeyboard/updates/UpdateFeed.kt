package com.rheerkens.androidkeyboard.updates

import java.io.StringReader
import java.util.Properties

/**
 * A release newer than the installed app, as described by the `update.properties` file that CI
 * attaches to every GitHub release:
 *
 * ```
 * versionCode=42
 * versionName=0.5.1
 * url=https://github.com/opstudio-eu/lowkey/releases/download/v0.5.1/lowkey-0.5.1.apk
 * size=87993840
 * sha256=<64 lowercase hex digits>
 * ```
 */
data class AvailableUpdate(
    val versionCode: Long,
    val versionName: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
) {
    /** The APK's file name, the last segment of [url]. */
    val fileName: String get() = url.substringAfterLast('/')

    /** The release page on GitHub, for "What's new". */
    val releasePage: String get() = "$RELEASE_PAGE_PREFIX$TAG_PREFIX$versionName"

    fun toProperties(): String = buildString {
        append("versionCode=").append(versionCode).append('\n')
        append("versionName=").append(versionName).append('\n')
        append("url=").append(url).append('\n')
        append("size=").append(sizeBytes).append('\n')
        append("sha256=").append(sha256).append('\n')
    }

    companion object {
        /** Release downloads must come from this repository, over HTTPS. */
        const val DOWNLOAD_PREFIX = "https://github.com/opstudio-eu/lowkey/releases/download/"
        private const val RELEASE_PAGE_PREFIX = "https://github.com/opstudio-eu/lowkey/releases/tag/"
        private const val TAG_PREFIX = "v"

        /** A release APK is about 90 MB; anything far larger is not ours. */
        private const val MAX_SIZE_BYTES = 512L * 1024 * 1024
        private val SHA256_HEX = Regex("[0-9a-f]{64}")
        private val VERSION_NAME = Regex("[0-9A-Za-z][0-9A-Za-z.+-]{0,39}")
        private val FILE_NAME = Regex("[0-9A-Za-z][0-9A-Za-z._-]{0,99}\\.apk")

        /** Parses and validates the feed. Throws [IllegalArgumentException] with the reason. */
        fun parse(text: String): AvailableUpdate {
            val p = Properties().apply { load(StringReader(text)) }
            fun field(key: String): String =
                requireNotNull(p.getProperty(key)?.trim()?.takeIf { it.isNotEmpty() }) { "missing $key" }

            val versionCode = requireNotNull(field("versionCode").toLongOrNull()) { "versionCode is not a number" }
            val versionName = field("versionName")
            val url = field("url")
            val size = requireNotNull(field("size").toLongOrNull()) { "size is not a number" }
            val sha256 = field("sha256")
            require(versionCode > 0) { "versionCode must be positive" }
            require(VERSION_NAME.matches(versionName)) { "invalid versionName" }
            require(url.startsWith(DOWNLOAD_PREFIX)) { "url is not a release download of this app" }
            val path = url.removePrefix(DOWNLOAD_PREFIX).split('/')
            require(path.size == 2 && path[0] == TAG_PREFIX + versionName) { "url does not match versionName" }
            require(FILE_NAME.matches(path[1])) { "url does not name an APK" }
            require(size in 1..MAX_SIZE_BYTES) { "size out of range" }
            require(SHA256_HEX.matches(sha256)) { "sha256 is not 64 lowercase hex digits" }
            return AvailableUpdate(versionCode, versionName, url, size, sha256)
        }
    }
}
