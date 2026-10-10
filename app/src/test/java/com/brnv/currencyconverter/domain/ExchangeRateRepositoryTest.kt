package com.brnv.currencyconverter.domain

import java.io.IOException
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ExchangeRateRepositoryTest {
    private val now = Instant.parse("2026-10-10T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val fresh = RateSnapshot("USD", mapOf("USD" to BigDecimal.ONE, "EUR" to BigDecimal("0.9")),
        now.minusSeconds(3600), now.plusSeconds(20_000), now)
    private val stale = fresh.copy(updatedAt = now.minusSeconds(172800),
        nextUpdateAt = now.minusSeconds(86400), fetchedAt = now.minusSeconds(172800))

    private class MemoryCache(var value: RateSnapshot? = null) : RateCache {
        override suspend fun read() = value
        override suspend fun write(snapshot: RateSnapshot) { value = snapshot }
    }

    @Test fun freshCacheAvoidsNetworkEvenOnRepeatedRefresh() = runBlocking {
        var requests = 0
        val repository = CachedExchangeRateRepository(RateDataSource { requests++; fresh }, MemoryCache(fresh), clock)
        assertTrue(repository.load().cached)
        repository.load(force = true)
        assertEquals(0, requests)
    }

    @Test fun staleCacheIsAvailableBeforeRefreshAndSurvivesFailure() = runBlocking {
        var requests = 0
        val repository = CachedExchangeRateRepository(RateDataSource { requests++; throw IOException() }, MemoryCache(stale), clock)
        assertEquals(stale, repository.cached())
        val result = repository.load()
        assertEquals(stale, result.snapshot)
        assertTrue(result.cached && result.refreshFailed)
        repository.load(force = true)
        assertEquals(1, requests)
    }

    @Test fun firstLaunchWithoutInternetHasNoFabricatedRates() = runBlocking {
        val repository = CachedExchangeRateRepository(RateDataSource { throw IOException() }, MemoryCache(), clock)
        assertNull(repository.load().snapshot)
        assertTrue(repository.load().refreshFailed)
    }

    @Test fun concurrentRequestsFetchAndPersistOnlyOnce() = runBlocking {
        var requests = 0
        val cache = MemoryCache()
        val repository = CachedExchangeRateRepository(RateDataSource { requests++; fresh }, cache, clock)
        List(8) { async { repository.load() } }.awaitAll()
        assertEquals(1, requests)
        assertEquals(fresh, cache.value)
        val restarted = CachedExchangeRateRepository(RateDataSource { error("No network expected") }, cache, clock)
        assertEquals(fresh, restarted.load().snapshot)
    }

    @Test fun diskFailureDoesNotDiscardSuccessfulNetworkResponse() = runBlocking {
        val cache = object : RateCache {
            override suspend fun read(): RateSnapshot? = null
            override suspend fun write(snapshot: RateSnapshot) { throw IOException() }
        }
        val result = CachedExchangeRateRepository(RateDataSource { fresh }, cache, clock).load()
        assertEquals(fresh, result.snapshot)
        assertTrue(result.cacheWriteFailed)
        assertFalse(result.refreshFailed)
    }

    @Test fun cancellationIsNotReportedAsOffline() = runBlocking {
        val repository = CachedExchangeRateRepository(RateDataSource { throw CancellationException() }, MemoryCache(), clock)
        try { repository.load(); fail("Cancellation must propagate") } catch (_: CancellationException) { }
    }
}
