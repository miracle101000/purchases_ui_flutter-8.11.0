package com.revenuecat.purchases_ui_flutter.views

import android.app.Activity
import android.content.Context
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.StandardMessageCodec
import io.flutter.plugin.platform.PlatformView
import io.flutter.plugin.platform.PlatformViewFactory

internal class PaywallViewFactory(
        private val messenger: BinaryMessenger,
        private val activity: () -> Activity?,
) : PlatformViewFactory(StandardMessageCodec.INSTANCE) {
        override fun create(context: Context, viewId: Int, args: Any?): PlatformView {
                @Suppress("UNCHECKED_CAST")
                val creationParams = args as? Map<String?, Any?>? ?: emptyMap()
                return PaywallView(
                        context = activity() ?: context,
                        id = viewId,
                        messenger = messenger,
                        creationParams = creationParams,
                )
        }
}
