package com.revenuecat.purchases_ui_flutter.views

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import java.util.Locale

private const val TAG = "RC_ContextBuilder"

/**
 * Holds the localized context and a [restore] lambda that **must** be called in the consuming
 * view's `dispose()`. The restore reverts both the Activity's and Application's AssetManager back
 * to their original configurations.
 */
internal data class LocalizedContext(
        val context: Context,
        val restore: () -> Unit,
)

/**
 * Builds a themed + localised [Context] from [activity].
 *
 * ### Why we prime both Activity AND Application resources
 *
 * `createConfigurationContext()` creates a new `Resources` wrapper but shares the underlying
 * `AssetManager`. The `AssetManager` only has locale-specific resource tables loaded ("warm") for
 * locales that have been active as the system locale at some point in this process run. Any other
 * locale falls back silently.
 *
 * Calling `updateConfiguration()` on the Activity's `Resources` primes its `AssetManager`, but
 * Compose and RevenueCat's SDK also access resources via the Application context — which can have a
 * separate `Resources` instance pointing to the same underlying `AssetManager`. Priming only the
 * Activity is insufficient if any internal code path goes through
 * `context.applicationContext.resources`.
 *
 * Priming both guarantees the `AssetManager` has the target locale loaded regardless of which
 * context path the SDK uses internally.
 *
 * ### Why restore is deferred to dispose()
 *
 * Compose resolves string resources lazily during recomposition, not at construction time.
 * Restoring the `AssetManager` immediately after construction reverts it before any resource
 * lookups actually occur. The restore must be deferred until the view is torn down via `dispose()`.
 *
 * @param theme Optional "dark" / "light" string from Flutter. Null → system default.
 * @param locale Optional BCP-47 language tag (e.g. "fr", "es-MX") from Flutter.
 * ```
 *               Null or unrecognised → falls back to the current system locale.
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

    val targetConfig =
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

    // Snapshot originals before mutating anything.
    val activityResources = activity.resources
    val appResources = activity.applicationContext.resources
    val originalActivityConfig = Configuration(activityResources.configuration)
    val originalAppConfig = Configuration(appResources.configuration)
    val originalDefaultLocale = Locale.getDefault()
    val activityDisplayMetrics = activityResources.displayMetrics
    val appDisplayMetrics = appResources.displayMetrics

    // Prime both Resources/AssetManager paths so that createConfigurationContext
    // can serve the target locale regardless of which code path the SDK uses.
    Log.d(TAG, "Priming Activity + Application AssetManagers for locale=$resolvedLocale")
    @Suppress("DEPRECATION")
    activityResources.updateConfiguration(targetConfig, activityDisplayMetrics)
    @Suppress("DEPRECATION") appResources.updateConfiguration(targetConfig, appDisplayMetrics)

    // JVM default — influences Compose plural rules / number formatting.
    Locale.setDefault(resolvedLocale)
    Log.d(TAG, "Locale.setDefault → $resolvedLocale (was $originalDefaultLocale)")

    val localizedContext = activity.createConfigurationContext(targetConfig)
    Log.d(
            TAG,
            "createConfigurationContext complete — locale=$resolvedLocale, uiMode=${targetConfig.uiMode}"
    )

    return LocalizedContext(
            context = ActivityContextWrapper(activity, localizedContext),
            restore = {
                Log.d(
                        TAG,
                        "dispose: restoring Activity + Application AssetManagers → $systemLocale"
                )
                @Suppress("DEPRECATION")
                activityResources.updateConfiguration(
                        originalActivityConfig,
                        activityDisplayMetrics
                )
                @Suppress("DEPRECATION")
                appResources.updateConfiguration(originalAppConfig, appDisplayMetrics)
                Locale.setDefault(originalDefaultLocale)
                Log.d(TAG, "dispose: restore complete, Locale.setDefault → $originalDefaultLocale")
            },
    )
}
