package com.brnv.currencyconverter.domain

import java.time.Duration
import org.junit.Assert.*
import org.junit.Test

class ReviewPolicyTest {
    private val now = Duration.ofDays(200).toMillis()
    private val experienced = ReviewUsage(now - Duration.ofDays(3).toMillis(), 3, 10, 180_000, 0)

    @Test fun requiresTimeAndMeaningfulUsage() {
        assertTrue(ReviewPolicy.eligible(experienced, now))
        assertFalse(ReviewPolicy.eligible(experienced.copy(firstUse = now), now))
        assertFalse(ReviewPolicy.eligible(experienced.copy(sessions = 2), now))
        assertFalse(ReviewPolicy.eligible(experienced.copy(conversions = 9), now))
        assertFalse(ReviewPolicy.eligible(experienced.copy(foregroundMillis = 179_999), now))
    }
    @Test fun requestCooldownIsNinetyDaysEvenWhenNoReviewWasSubmitted() {
        assertFalse(ReviewPolicy.eligible(experienced.copy(lastRequest = now - Duration.ofDays(89).toMillis()), now))
        assertTrue(ReviewPolicy.eligible(experienced.copy(lastRequest = now - Duration.ofDays(90).toMillis()), now))
    }
    @Test fun clockMovingBackwardsDoesNotTriggerPrompt() {
        assertFalse(ReviewPolicy.eligible(experienced.copy(firstUse = now + 1), now))
        assertFalse(ReviewPolicy.eligible(experienced.copy(lastRequest = now + 1), now))
    }
}
