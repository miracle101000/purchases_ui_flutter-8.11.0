package com.revenuecat.purchases_ui_flutter.views

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Resources

internal class ActivityContextWrapper(
        private val activity: Activity,
        private val themedContext: Context
) : ContextWrapper(activity) {
    override fun getResources(): Resources = themedContext.resources
    override fun getTheme(): Resources.Theme = themedContext.theme
    override fun getAssets(): AssetManager = themedContext.assets
}
