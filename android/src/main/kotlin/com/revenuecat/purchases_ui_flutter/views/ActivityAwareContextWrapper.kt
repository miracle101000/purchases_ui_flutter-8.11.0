package com.revenuecat.purchases_ui_flutter.views

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Resources
import android.util.Log
import android.view.LayoutInflater

private const val TAG = "RC_ActivityCtxWrapper"

internal class ActivityContextWrapper(
        private val activity: Activity,
        private val themedContext: Context
) : ContextWrapper(activity) {

    init {
        Log.d(
                TAG,
                "ActivityContextWrapper created — wrapping activity=${activity::class.simpleName}"
        )
    }

    override fun getResources(): Resources = themedContext.resources
    override fun getTheme(): Resources.Theme = themedContext.theme
    override fun getAssets(): AssetManager = themedContext.assets

    override fun getSystemService(name: String): Any? {
        // Critical: Native views use the LayoutInflater to resolve resources.
        // If we don't proxy this, they use the Activity's default inflater.
        return if (Context.LAYOUT_INFLATER_SERVICE == name) {
            Log.d(TAG, "getSystemService: proxying LAYOUT_INFLATER_SERVICE to themedContext")
            LayoutInflater.from(themedContext)
        } else {
            super.getSystemService(name)
        }
    }
}
