package com.revenuecat.purchases_ui_flutter.views

import android.app.Activity
import android.content.Context
import android.util.Log
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

private const val TAG = "RC_PaywallViewFactory"

internal class PaywallViewFactory(
        private val messenger: BinaryMessenger,
        private val activity: () -> Activity?,
) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
    override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
        @Suppress("UNCHECKED_CAST") val creationParams = args as? Map<String?, Any?>? ?: emptyMap()
        val resolvedActivity =
                activity()
                        ?: run {
                            Log.e(
                                    TAG,
                                    "create(viewId=$viewId): activity is null — cannot create PaywallView"
                            )
                            error(
                                    "PaywallViewFactory requires an Activity context, but none is currently attached. " +
                                            "Ensure MainActivity inherits from FlutterFragmentActivity."
                            )
                        }
        Log.d(TAG, "create(viewId=$viewId): using activity=${resolvedActivity::class.simpleName}")
        return PaywallView(
                context = resolvedActivity,
                id = viewId,
                messenger = messenger,
                creationParams = creationParams,
        )
    }
}
