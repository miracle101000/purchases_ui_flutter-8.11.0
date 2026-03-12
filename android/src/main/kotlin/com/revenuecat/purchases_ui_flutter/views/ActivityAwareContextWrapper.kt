package com.revenuecat.purchases_ui_flutter.views

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle

internal class ActivityAwareContextWrapper(base: Context, private val activity: Activity) :
        ContextWrapper(base) {
    override fun startActivity(intent: Intent) {
        activity.startActivity(intent)
    }
    override fun startActivity(intent: Intent, options: Bundle?) {
        activity.startActivity(intent, options)
    }
}
