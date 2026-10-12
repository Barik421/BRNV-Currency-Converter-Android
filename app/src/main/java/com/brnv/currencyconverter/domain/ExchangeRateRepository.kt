package com.brnv.currencyconverter.domain

import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

fun interface RateDataSource { suspend fun fetch(): RateSnapshot }
interface RateCache {
    suspend fun read(): RateSnapshot?
    suspend fun write(snapshot: RateSnapshot)
}

data class RateResult(
    val snapshot: RateSnapshot?,
    val cached: Boolean = true,
    val refreshFailed: Boolean = false,
    val cacheWriteFailed: Boolean = false,
)

interface ExchangeRateRepository {
    suspend fun cached(): RateSnapshot?
    suspend fun load(force: Boolean = false): RateResult
}

/** Application-scoped; concurrent app/widget requests share one cache and one request. */
class CachedExchangeRateRepository(
    private val source: RateDataSource,
    private val cache: RateCache,
    private val clock: Clock = Clock.systemUTC(),
    private val minimumRefreshInterval: Duration = Duration.ofHours(1),
    private val maximumCacheAge: Duration = Duration.ofDays(1),
) : ExchangeRateRepository {
    private val mutex = Mutex()
    private var memory: RateSnapshot? = null
    private var retryAfter = Instant.MIN
    private var lastFailure = false

    override suspend fun cached(): RateSnapshot? = mutex.withLock { readCache() }

    private suspend fun readCache(): RateSnapshot? {
        if (memory == null) memory = try { cache.read() } catch (_: IOException) { null }
        return memory
    }

    override suspend fun load(force: Boolean): RateResult = mutex.withLock {
        val previous = readCache()
        val now = clock.instant()
        // Each provider has its own refresh budget, including explicit refreshes.
        val fresh = previous != null && now >= previous.fetchedAt &&
            now < minOf(previous.nextUpdateAt, previous.fetchedAt.plus(maximumCacheAge))
        val throttled = previous != null && now >= previous.fetchedAt &&
            now < previous.fetchedAt.plus(minimumRefreshInterval)
        if (now < retryAfter || throttled || (!force && fresh)) {
            return@withLock RateResult(previous, refreshFailed = lastFailure)
        }
        try {
            val snapshot = source.fetch()
            memory = snapshot
            lastFailure = false
            retryAfter = Instant.MIN
            val failedToSave = try { cache.write(snapshot); false } catch (_: IOException) { true }
            RateResult(snapshot, cached = false, cacheWriteFailed = failedToSave)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: IOException) {
            lastFailure = true
            retryAfter = now.plusSeconds(60)
            RateResult(previous, refreshFailed = true)
        }
    }
}
