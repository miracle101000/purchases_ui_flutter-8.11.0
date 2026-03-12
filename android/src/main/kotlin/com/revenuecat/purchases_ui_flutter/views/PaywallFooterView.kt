package com.revenuecat.purchases_ui_flutter.views

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.children
import com.revenuecat.purchases.hybridcommon.ui.PaywallListenerWrapper
import com.revenuecat.purchases.ui.revenuecatui.views.PaywallFooterView as NativePaywallFooterView
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView

internal class PaywallFooterView(
        context: Context,
        id: Int,
        messenger: BinaryMessenger,
        creationParams: Map<String?, Any?>
) : PlatformView {

    private val methodChannel: MethodChannel
    private val nativePaywallFooterView: NativePaywallFooterView

    override fun getView(): View = nativePaywallFooterView
    override fun dispose() {}

    init {
        methodChannel = MethodChannel(messenger, "com.revenuecat.purchasesui/PaywallFooterView/$id")

        val offeringIdentifier = creationParams["offeringIdentifier"] as String?
        val theme = creationParams["theme"] as String?

        val activity =
                context as? Activity ?: error("PaywallFooterView requires an Activity context")

        val themedContext: Context =
                if (theme != null) {
                    val nightMode =
                            if (theme == "dark") Configuration.UI_MODE_NIGHT_YES
                            else Configuration.UI_MODE_NIGHT_NO
                    val config =
                            Configuration(activity.resources.configuration).also {
                                it.uiMode =
                                        (it.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                                                nightMode
                            }
                    ActivityAwareContextWrapper(
                            activity.createConfigurationContext(config),
                            activity
                    )
                } else {
                    activity
                }

        nativePaywallFooterView =
                object :
                        NativePaywallFooterView(
                                themedContext,
                                dismissHandler = { methodChannel.invokeMethod("onDismiss", null) }
                        ) {
                    public override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                        var maxWidth = 0
                        var maxHeight = 0
                        children.forEach {
                            it.measure(widthMeasureSpec, MeasureSpec.UNSPECIFIED)
                            maxWidth = maxWidth.coerceAtLeast(it.measuredWidth)
                            maxHeight = maxHeight.coerceAtLeast(it.measuredHeight)
                        }
                        val finalWidth = maxWidth.coerceAtLeast(suggestedMinimumWidth)
                        val finalHeight = maxHeight.coerceAtLeast(suggestedMinimumHeight)
                        setMeasuredDimension(finalWidth, finalHeight)
                        updateHeight(finalHeight.toDouble())
                    }
                }

        nativePaywallFooterView.setPaywallListener(
                object : PaywallListenerWrapper() {
                    override fun onPurchaseStarted(rcPackage: Map<String, Any?>) {
                        methodChannel.invokeMethod("onPurchaseStarted", rcPackage)
                    }
                    override fun onPurchaseCompleted(
                            customerInfo: Map<String, Any?>,
                            storeTransaction: Map<String, Any?>
                    ) {
                        methodChannel.invokeMethod(
                                "onPurchaseCompleted",
                                mapOf(
                                        "customerInfo" to customerInfo,
                                        "storeTransaction" to storeTransaction
                                )
                        )
                    }
                    override fun onPurchaseCancelled() {
                        methodChannel.invokeMethod("onPurchaseCancelled", null)
                    }
                    override fun onPurchaseError(error: Map<String, Any?>) {
                        methodChannel.invokeMethod("onPurchaseError", error)
                    }
                    override fun onRestoreCompleted(customerInfo: Map<String, Any?>) {
                        methodChannel.invokeMethod("onRestoreCompleted", customerInfo)
                    }
                    override fun onRestoreError(error: Map<String, Any?>) {
                        methodChannel.invokeMethod("onRestoreError", error)
                    }
                }
        )

        nativePaywallFooterView.layoutParams =
                FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        Gravity.BOTTOM
                )
        nativePaywallFooterView.setOfferingId(offeringIdentifier)
    }

    private fun updateHeight(newHeight: Double) {
        methodChannel.invokeMethod("onHeightChanged", newHeight)
    }
}
