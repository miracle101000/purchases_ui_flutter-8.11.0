package com.revenuecat.purchases_ui_flutter.views

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * Builds a themed + localised [Context] from [activity].
 *
 * @param theme Optional "dark" / "light" string from Flutter. Null → system default.
 * @param locale Optional BCP-47 language tag (e.g. "fr", "es-MX") from Flutter.
 * ```
 *                Null or unrecognised → falls back to [Locale.ENGLISH].
 * ```
 */
internal fun buildFinalContext(
        activity: Activity,
        theme: String?,
        locale: String?,
): Context {
    val resolvedLocale: Locale =
            if (!locale.isNullOrBlank()) {
                val parsed = Locale.forLanguageTag(locale)
                // forLanguageTag returns an empty Locale for unrecognised tags
                if (parsed.language.isNotEmpty()) parsed else Locale.ENGLISH
            } else {
                Locale.ENGLISH
            }

    val config =
            Configuration(activity.resources.configuration).apply {
                // Apply night-mode override when a theme is requested
                if (theme != null) {
                    val nightMode =
                            if (theme == "dark") Configuration.UI_MODE_NIGHT_YES
                            else Configuration.UI_MODE_NIGHT_NO
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
                }
                setLocale(resolvedLocale)
            }

    return ActivityContextWrapper(activity, activity.createConfigurationContext(config))
}
