package com.astra.hud.data.source

import com.astra.hud.contracts.NotificationFeed
import com.astra.hud.model.RawEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-wide notification bus. [com.astra.hud.data.AstraNotificationListener] publishes here;
 * the app observes [events] or reads [snapshot]. Keeps a ring buffer of the last [CAPACITY]
 * events, deduplicated by [RawEvent.contentHash].
 */
object NotificationHub : NotificationFeed {
  const val CAPACITY = 200

  private val lock = Any()
  private val buffer = ArrayDeque<RawEvent>(CAPACITY)
  private val hashes = HashSet<String>()
  private val flow = MutableSharedFlow<RawEvent>(extraBufferCapacity = 64)

  override val events: Flow<RawEvent> = flow.asSharedFlow()

  /** Newest first. */
  override fun snapshot(): List<RawEvent> = synchronized(lock) { buffer.reversed() }

  /** Buffers and emits [event] unless an event with the same contentHash is already buffered. */
  fun publish(event: RawEvent) {
    synchronized(lock) {
      if (!hashes.add(event.contentHash)) return
      buffer.addLast(event)
      while (buffer.size > CAPACITY) {
        hashes.remove(buffer.removeFirst().contentHash)
      }
    }
    flow.tryEmit(event)
  }

  /** Clears the buffer (mainly for tests). */
  fun clear() = synchronized(lock) {
    buffer.clear()
    hashes.clear()
  }
}
