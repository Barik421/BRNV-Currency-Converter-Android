package com.brnv.currencyconverter.review

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import android.widget.Toast
import com.brnv.currencyconverter.R
import com.brnv.currencyconverter.domain.ReviewPolicy
import com.brnv.currencyconverter.domain.ReviewUsage
import com.google.android.play.core.review.ReviewManagerFactory
import androidx.core.content.edit
import androidx.core.net.toUri
import java.time.Duration

/** Local usage counters only. No opinion questions, review gating or analytics transmission. */
class ReviewCoordinator(private val prefs: SharedPreferences) {
    private var resumedAt: Long? = null
    private var requestRunning = false

    fun resume() {
        if (resumedAt != null) return
        resumedAt = SystemClock.elapsedRealtime()
        val now = System.currentTimeMillis()
        prefs.edit {
            if (!prefs.contains("first_use")) putLong("first_use", now)
            if (now - prefs.getLong("last_session", 0) >= Duration.ofMinutes(30).toMillis()) {
                putInt("sessions", prefs.getInt("sessions", 0) + 1)
            }
            putLong("last_session", now)
        }
    }

    fun pause() {
        val start = resumedAt ?: return
        prefs.edit {
            putLong("foreground", prefs.getLong("foreground", 0) +
                (SystemClock.elapsedRealtime() - start).coerceAtLeast(0))
            putLong("last_session", System.currentTimeMillis())
        }
        resumedAt = null
    }

    fun recordConversion() {
        val now = System.currentTimeMillis()
        // A run of keystrokes is one usage event, not dozens of conversions.
        if (now - prefs.getLong("last_conversion", 0) < 30_000) return
        prefs.edit {
            putLong("last_conversion", now)
            putInt("conversions", prefs.getInt("conversions", 0) + 1)
        }
    }

    fun maybeRequest(activity: Activity) {
        val start = resumedAt ?: return
        if (requestRunning || activity.isFinishing || activity.isDestroyed) return
        val now = System.currentTimeMillis()
        val usage = ReviewUsage(prefs.getLong("first_use", now), prefs.getInt("sessions", 0),
            prefs.getInt("conversions", 0), prefs.getLong("foreground", 0) +
                (SystemClock.elapsedRealtime() - start).coerceAtLeast(0), prefs.getLong("last_request", 0))
        if (!ReviewPolicy.eligible(usage, now)) return
        // Sideloaded APKs cannot submit Play reviews. Never show a broken automatic prompt.
        @Suppress("DEPRECATION")
        val installer = activity.packageManager.getInstallerPackageName(activity.packageName)
        if (installer != "com.android.vending") return
        requestRunning = true
        prefs.edit { putLong("last_request", now) }
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { request ->
            if (request.isSuccessful && resumedAt != null && !activity.isFinishing && !activity.isDestroyed) {
                manager.launchReviewFlow(activity, request.result).addOnCompleteListener {
                    // Completion does not mean the card was shown or a review was submitted.
                    requestRunning = false
                }
            } else requestRunning = false
        }
    }

    fun openStore(context: Context) {
        prefs.edit { putLong("last_request", System.currentTimeMillis()) }
        val market = Intent(Intent.ACTION_VIEW, "market://details?id=${context.packageName}".toUri())
        try { context.startActivity(market) } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW,
                    "https://play.google.com/store/apps/details?id=${context.packageName}".toUri()))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, R.string.store_unavailable, Toast.LENGTH_LONG).show()
            }
        }
    }
}
