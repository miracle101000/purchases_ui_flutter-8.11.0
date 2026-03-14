package com.revenuecat.purchases_ui_flutter.views

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Resources
import android.view.LayoutInflater

internal class ActivityContextWrapper(
        private val activity: Activity,
        private val themedContext: Context
) : ContextWrapper(activity) {
    override fun getResources(): Resources = themedContext.resources
    override fun getTheme(): Resources.Theme = themedContext.theme
    override fun getAssets(): AssetManager = themedContext.assets

    override fun getSystemService(name: String): Any? {
        // Critical: Native views use the LayoutInflater to resolve resources.
        // If we don't proxy this, they use the Activity's default inflater.
        return if (Context.LAYOUT_INFLATER_SERVICE == name) {
            LayoutInflater.from(themedContext)
        } else {
            super.getSystemService(name)
        }
    }
}
