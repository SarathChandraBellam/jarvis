package com.jarvis.hud.data.source

import com.jarvis.hud.model.DataSource
import java.security.MessageDigest

/**
 * Stable content hashing used to dedupe [com.jarvis.hud.model.RawEvent]s across scans.
 */
object ContentHash {
  /** SHA-256 hex of `source + sender + body + timestamp`. */
  fun of(source: DataSource, sender: String, body: String, timestamp: Long): String =
    sha256Hex("${source.name}|$sender|$body|$timestamp")

  /** Lower-case hex SHA-256 of the UTF-8 bytes of [input]. */
  fun sha256Hex(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    val sb = StringBuilder(digest.size * 2)
    for (b in digest) {
      val v = b.toInt() and 0xff
      sb.append(HEX[v ushr 4]).append(HEX[v and 0x0f])
    }
    return sb.toString()
  }

  private val HEX = "0123456789abcdef".toCharArray()
}
