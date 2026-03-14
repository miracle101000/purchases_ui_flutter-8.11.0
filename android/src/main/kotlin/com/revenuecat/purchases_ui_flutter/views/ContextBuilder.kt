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
    return ActivityContextWrapper(activity, activity.createConfigurationContext(config))
}
