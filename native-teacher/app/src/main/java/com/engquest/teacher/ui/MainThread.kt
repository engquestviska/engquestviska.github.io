package com.engquest.teacher.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlin.coroutines.ContinuationInterceptor

/**
 * Runs [block] on the main thread, through the dispatcher of this screen's own scope (the one
 * Compose itself runs on). Screens keep every piece of on-screen state, and the counters that
 * guard it, on the main thread; work for the outbox runs elsewhere and comes back through here.
 * It still runs when the calling work was cancelled, so a counter is never left behind.
 */
internal suspend fun <T> CoroutineScope.onMain(block: () -> T): T =
    withContext(NonCancellable + (coroutineContext[ContinuationInterceptor] ?: Dispatchers.Main.immediate)) { block() }
