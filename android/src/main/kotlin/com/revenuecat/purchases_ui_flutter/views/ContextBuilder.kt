package com.revenuecat.purchases_ui_flutter.views

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import java.util.Locale

private const val TAG = "RC_ContextBuilder"

/**
 * Builds a themed + localised [Context] from [activity].
 *
 * @param theme Optional "dark" / "light" string from Flutter. Null → system default.
 * @param locale Optional BCP-47 language tag (e.g. "fr", "es-MX") from Flutter.
 * ```
 *                Null or unrecognised → falls back to the current system locale.
 * ```
 */
internal fun buildFinalContext(
        activity: Activity,
        theme: String?,
        locale: String?,
): Context {
    val systemLocale: Locale = activity.resources.configuration.locales[0]
    Log.d(
            TAG,
            "buildFinalContext called — theme=$theme, locale=$locale, systemLocale=$systemLocale"
    )

    val resolvedLocale: Locale =
            if (!locale.isNullOrBlank()) {
                val normalizedTag = locale.replace("_", "-")
                val parsed = Locale.forLanguageTag(normalizedTag)

                if (parsed.language.isNotEmpty()) {
                    Log.d(TAG, "Resolved locale from Flutter tag '$locale' → $parsed")
                    parsed
                } else {
                    Log.w(
                            TAG,
                            "Could not parse locale tag '$locale', falling back to system locale: $systemLocale"
                    )
                    systemLocale
                }
            } else {
                Log.d(TAG, "No locale supplied by Flutter, using system locale: $systemLocale")
                systemLocale
            }

    val config =
            Configuration(activity.resources.configuration).apply {
                // Apply night-mode override when a theme is requested
                if (theme != null) {
                    val nightMode =
                            if (theme == "dark") Configuration.UI_MODE_NIGHT_YES
                            else Configuration.UI_MODE_NIGHT_NO
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
                    Log.d(TAG, "Applied theme override: $theme (nightMode=$nightMode)")
                } else {
                    Log.d(TAG, "No theme override supplied, using system default")
                }
                setLocale(resolvedLocale)
            }

    Log.d(
            TAG,
            "Creating configuration context with locale=$resolvedLocale, uiMode=${config.uiMode}"
    )

    // -----------------------------------------------------------------------
    // AssetManager locale-priming workaround
    //
    // createConfigurationContext() shares the Activity's AssetManager. The
    // AssetManager only has locale-specific resources "warm" for locales that
    // have already been the system locale at some point in this process run.
    // For any other locale (e.g. user picks Italian while the device is set to
    // French) createConfigurationContext produces a context whose resource
    // lookups silently fall back to the default (English) strings.
    //
    // Fix: briefly call the deprecated updateConfiguration() to force the
    // AssetManager to initialise resources for the target locale, then
    // immediately restore the original configuration. After this, the
    // subsequent createConfigurationContext() call works correctly.
    // -----------------------------------------------------------------------
    val originalConfig = Configuration(activity.resources.configuration)
    val displayMetrics = activity.resources.displayMetrics
    Log.d(TAG, "Priming AssetManager for locale=$resolvedLocale")
    @Suppress("DEPRECATION") activity.resources.updateConfiguration(config, displayMetrics)
    val localizedContext = activity.createConfigurationContext(config)
    Log.d(TAG, "Restoring original configuration after AssetManager priming")
    @Suppress("DEPRECATION") activity.resources.updateConfiguration(originalConfig, displayMetrics)

    return ActivityContextWrapper(activity, localizedContext)
}
