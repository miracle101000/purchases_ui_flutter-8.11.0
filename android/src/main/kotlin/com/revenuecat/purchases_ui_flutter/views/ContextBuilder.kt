package com.revenuecat.purchases_ui_flutter.views

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import android.view.ContextThemeWrapper
import java.util.Locale

private const val TAG = "RC_ContextBuilder"

/**
 * Holds the localized context and a [restore] lambda that must be called when the consuming view is
 * disposed (to restore the JVM default locale).
 */
internal data class LocalizedContext(
        val context: Context,
        val restore: () -> Unit,
)

/**
 * Builds a themed + localised [Context] using [ContextThemeWrapper.applyOverrideConfiguration].
 *
 * ### Why ContextThemeWrapper instead of createConfigurationContext
 *
 * `createConfigurationContext()` shares the Activity's `AssetManager`, which only has locale
 * resources loaded for locales that have previously been the system locale in this process run. Any
 * other locale silently falls back to English.
 *
 * `ContextThemeWrapper.applyOverrideConfiguration()` is the approach AppCompat uses internally for
 * dark-mode and locale overrides. It constructs a fresh `Resources` instance via
 * `ResourcesManager`, bypassing the shared AssetManager limitation and correctly loading resources
 * for any locale without side effects on the Activity.
 *
 * @param theme Optional "dark" / "light" string from Flutter. Null -> system default.
 * @param locale Optional BCP-47 language tag (e.g. "fr", "es-MX") from Flutter.
 * ```
 *               Null or unrecognised -> falls back to the current system locale.
 * ```
 */
internal fun buildFinalContext(
        activity: Activity,
        theme: String?,
        locale: String?,
): LocalizedContext {
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
                    Log.d(TAG, "Resolved locale from Flutter tag '$locale' -> $parsed")
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

    // Build an override Configuration containing only the deltas we want to apply.
    // ContextThemeWrapper merges this on top of the base context's configuration,
    // so we only need to specify what we're changing.
    val overrideConfig = Configuration()

    if (theme != null) {
        val nightMode =
                if (theme == "dark") Configuration.UI_MODE_NIGHT_YES
                else Configuration.UI_MODE_NIGHT_NO
        // Mask in only the night-mode bits; leave all other uiMode bits at zero so the
        // wrapper merges them from the base rather than overriding them.
        overrideConfig.uiMode = nightMode or Configuration.UI_MODE_TYPE_UNDEFINED
        Log.d(TAG, "Applied theme override: $theme (nightMode=$nightMode)")
    } else {
        Log.d(TAG, "No theme override supplied, using system default")
    }

    overrideConfig.setLocale(resolvedLocale)

    // ContextThemeWrapper.applyOverrideConfiguration creates a fresh Resources/AssetManager
    // scoped to the merged configuration — no shared-AssetManager priming needed.
    val wrapper = ContextThemeWrapper(activity, activity.theme)
    wrapper.applyOverrideConfiguration(overrideConfig)
    Log.d(
            TAG,
            "ContextThemeWrapper created with locale=$resolvedLocale, uiMode=${overrideConfig.uiMode}"
    )

    // Locale.setDefault is JVM-wide and doesn't affect Android resource lookups, but
    // some Compose internals (plural rules, number formatting) consult it. Restore on dispose.
    val originalDefaultLocale = Locale.getDefault()
    Locale.setDefault(resolvedLocale)
    Log.d(TAG, "Locale.setDefault set to $resolvedLocale (was $originalDefaultLocale)")

    return LocalizedContext(
            context = wrapper,
            restore = {
                Locale.setDefault(originalDefaultLocale)
                Log.d(TAG, "Locale.setDefault restored to $originalDefaultLocale")
            },
    )
}
