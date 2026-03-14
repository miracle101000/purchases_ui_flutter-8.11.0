package com.revenuecat.purchases_ui_flutter.views

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import java.util.Locale

private const val TAG = "RC_ContextBuilder"

/**
 * Holds the localized context and a [restore] lambda that must be called when the consuming view is
 * disposed. The restore reverts the Activity's AssetManager back to its original configuration (see
 * [buildFinalContext] for why this is needed).
 */
internal data class LocalizedContext(
        val context: Context,
        val restore: () -> Unit,
)

/**
 * Builds a themed + localised [Context] from [activity] and returns it paired with a
 * [LocalizedContext.restore] lambda that the caller **must** invoke on view disposal.
 *
 * ### Why the restore lambda exists
 *
 * `createConfigurationContext()` does not create a new `AssetManager` — it shares the Activity's.
 * The shared `AssetManager` only has locale-specific resources loaded for locales that have
 * previously been active as the system locale in the current process. Any other locale falls
 * through to the default (English) strings.
 *
 * The fix is to call the deprecated `updateConfiguration()` before `createConfigurationContext()`,
 * which forces the `AssetManager` to load the target locale's resources. Crucially, this must
 * **not** be restored immediately: Compose resolves string resources lazily during recomposition,
 * so restoring the `AssetManager` straight after construction reverts it before any resource
 * lookups actually occur — which is exactly the bug this workaround is addressing.
 *
 * Instead, the caller stores [LocalizedContext.restore] and invokes it in `dispose()`, keeping the
 * `AssetManager` primed for the target locale for the entire lifespan of the view.
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

    val config =
            Configuration(activity.resources.configuration).apply {
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

    // Snapshot the original config so we can restore it on dispose.
    val originalConfig = Configuration(activity.resources.configuration)
    val displayMetrics = activity.resources.displayMetrics
    val originalDefaultLocale = Locale.getDefault()

    // Prime the shared AssetManager for the target locale. The restore is intentionally
    // deferred to dispose() — see the KDoc above for the full explanation.
    Log.d(TAG, "Priming AssetManager for locale=$resolvedLocale (restore deferred to dispose)")
    @Suppress("DEPRECATION") activity.resources.updateConfiguration(config, displayMetrics)
    // Also set the JVM default locale — this doesn't affect Android resource lookups
    // but may influence Compose internals or RevenueCat SDK formatting. Experimental.
    Locale.setDefault(resolvedLocale)
    Log.d(TAG, "Locale.setDefault set to $resolvedLocale (was $originalDefaultLocale)")

    Log.d(
            TAG,
            "Creating configuration context with locale=$resolvedLocale, uiMode=${config.uiMode}"
    )
    val localizedContext = activity.createConfigurationContext(config)

    return LocalizedContext(
            context = ActivityContextWrapper(activity, localizedContext),
            restore = {
                Log.d(TAG, "Restoring original AssetManager configuration (locale=$systemLocale)")
                @Suppress("DEPRECATION")
                activity.resources.updateConfiguration(originalConfig, displayMetrics)
                Locale.setDefault(originalDefaultLocale)
                Log.d(TAG, "Locale.setDefault restored to $originalDefaultLocale")
            },
    )
}
