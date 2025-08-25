/*
 * Copyright (c) 2025 NullPops
 *
 * This file is part of eventbus.
 *
 * Licensed under the GNU Affero General Public License v3.0 (AGPLv3)
 * or a Commercial License.
 *
 * You may use this file under AGPLv3 if you release your project under
 * a compatible open source license. For closed source or commercial use,
 * you must obtain a commercial license from NullPops.
 *
 * See the LICENSE file for details.
 */
package nullpops.events

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import nullpops.logger.Logger

/**
 * A mutable envelope passed to handlers.
 * Handlers can call [cancel] to stop further propagation.
 */
class EventEnvelope<T : Any>(val payload: T) {
    @Volatile private var _cancelled: Boolean = false
    val cancelled: Boolean get() = _cancelled
    fun cancel() { _cancelled = true }
}

typealias EventHandler<T> = (EventEnvelope<T>) -> Unit

/**
 * Immutable subscription descriptor.
 * Use the returned [SubscriptionHandle] from [EventBus.subscribe] to unsubscribe.
 */
data class Subscription<T : Any>(
    val eventType: Class<T>,
    val handler: EventHandler<T>,
    val priority: Int = 0,
    val tag: String = ""
)

/** Allows callers to unsubscribe without knowing internals. */
interface SubscriptionHandle {
    fun unsubscribe()
}

/**
 * EventBus: simple, fast, in-process pub/sub with priority ordering and cancellation.
 *
 * Notes:
 * - Dispatch is by the event's *concrete class* (no supertypes matching).
 * - Handlers run on the caller's thread.
 * - Higher priority runs first (desc).
 */
class EventBus(
    name: String = "eventbus"
) {
    private val logger = Logger("Events[$name]")

    // Mutable lists kept sorted by priority (desc). Synchronized per-list for updates.
    private val subscribers =
        ConcurrentHashMap<Class<*>, MutableList<Subscription<out Any>>>()

    // Read-only snapshots for hot path dispatch (no locking during post()).
    private val roSnapshots =
        ConcurrentHashMap<Class<*>, Array<Subscription<out Any>>>()

    @Suppress("UNUSED")
    /** Publish a payload (convenience). Returns number of handlers invoked. */
    fun <T : Any> publish(payload: T): Int = publish(EventEnvelope(payload))

    /** Publish an envelope. Returns number of handlers invoked. */
    fun <T : Any> publish(envelope: EventEnvelope<T>): Int {
        val key: Class<T> = envelope.payload.javaClass
        val snapshot = roSnapshots[key] ?: return 0

        var count = 0
        for (sub in snapshot) {
            if (envelope.cancelled) break
            try {
                @Suppress("UNCHECKED_CAST")
                (sub as Subscription<T>).handler(envelope)
                count++
            } catch (t: Throwable) {
                logger.error("Handler threw for event ${key.name} (tag='${sub.tag}', priority=${sub.priority})")
                t.printStackTrace()
            }
        }
        return count
    }

    /** Subscribe with reified type. Returns a handle you can call [SubscriptionHandle.unsubscribe]. */
    inline fun <reified T : Any> subscribe(
        priority: Int = 0,
        tag: String = "",
        noinline handler: EventHandler<T>
    ): SubscriptionHandle = subscribe(T::class.java, handler, priority, tag)

    /** Subscribe by explicit class. Returns an unsubscribe handle. */
    fun <T : Any> subscribe(
        eventType: Class<T>,
        handler: EventHandler<T>,
        priority: Int = 0,
        tag: String = ""
    ): SubscriptionHandle {
        val sub = Subscription(eventType, handler, priority, tag)

        val list = subscribers.computeIfAbsent(eventType) {
            Collections.synchronizedList(mutableListOf())
        }

        val added = synchronized(list) {
            // Prevent duplicate handlers for the same event type
            if (list.any { it.handler === handler }) {
                false
            } else {
                list.add(sub)
                // Highest priority first
                @Suppress("UNCHECKED_CAST")
                list.sortByDescending { (it as Subscription<Any>).priority }
                // Refresh read-only snapshot
                @Suppress("UNCHECKED_CAST")
                roSnapshots[eventType] = list.toTypedArray()
                true
            }
        }

        if (!added) {
            logger.warn(
                "Skipped duplicate subscriber for ${eventType.name} (tag='$tag'). " +
                        "A handler can only be subscribed once per event type."
            )
        }

        // Return a lightweight handle that removes this exact subscription.
        return object : SubscriptionHandle {
            override fun unsubscribe() {
                val lst = subscribers[eventType] ?: return
                val changed = synchronized(lst) {
                    val removed = lst.remove(sub)
                    if (removed) {
                        roSnapshots[eventType] =
                            if (lst.isEmpty()) {
                                subscribers.remove(eventType)
                                emptyArray()
                            } else {
                                lst.toTypedArray()
                            }
                    }
                    removed
                }
                if (!changed) {
                    logger.warn("Unsubscribe had no effect for ${eventType.name} (tag='$tag').")
                }
            }
        }
    }
}

@Suppress("UNUSED")
/** Main sub/pub handler */
val GlobalEventBus = EventBus(name = "main")
