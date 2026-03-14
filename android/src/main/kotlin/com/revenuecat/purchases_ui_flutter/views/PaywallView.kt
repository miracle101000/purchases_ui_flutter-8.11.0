package com.revenuecat.purchases_ui_flutter.views

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import com.revenuecat.purchases.hybridcommon.ui.PaywallListenerWrapper
import com.revenuecat.purchases.ui.revenuecatui.views.PaywallView as NativePaywallView
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.platform.PlatformView

private const val TAG = "RC_PaywallView"

internal class PaywallView(
        context: Context,
        private val id: Int,
        messenger: BinaryMessenger,
        creationParams: Map<String?, Any?>
) : PlatformView, MethodCallHandler {

    private val methodChannel: MethodChannel
    private val nativePaywallView: NativePaywallView
    private lateinit var restoreLocale: () -> Unit

    override fun getView(): View = nativePaywallView

    override fun dispose() {
        Log.d(TAG, "dispose called for view id=$id — restoring AssetManager locale")
        restoreLocale()
    }

    init {
        Log.d(TAG, "Initialising PaywallView id=$id, params=$creationParams")

        methodChannel = MethodChannel(messenger, "com.revenuecat.purchasesui/PaywallView/$id")
        methodChannel.setMethodCallHandler(this)

        val offeringIdentifier = creationParams["offeringIdentifier"] as String?
        val displayCloseButton = creationParams["displayCloseButton"] as Boolean?
        val theme = creationParams["theme"] as String?
        // BCP-47 locale tag supplied by Flutter (e.g. "fr", "es-MX").
        // Falls back to the system locale inside buildFinalContext when null or unrecognised.
        val locale = creationParams["locale"] as String?

        Log.d(
                TAG,
                "Creation params — offeringIdentifier=$offeringIdentifier, displayCloseButton=$displayCloseButton, theme=$theme, locale=$locale"
        )

        val activity = context as? Activity ?: error("PaywallView requires an Activity context")

        val (finalContext, restore) = buildFinalContext(activity, theme, locale)
        Log.d(TAG, "finalContext locale: ${finalContext.resources.configuration.locales[0]}")
        Log.d(TAG, "activity locale: ${activity.resources.configuration.locales[0]}")
        Log.d(
                TAG,
                "application locale: ${activity.applicationContext.resources.configuration.locales[0]}"
        )
        restoreLocale = restore

        nativePaywallView =
                NativePaywallView(
                        context = finalContext,
                        shouldDisplayDismissButton = displayCloseButton,
                        dismissHandler = {
                            Log.d(TAG, "onDismiss triggered")
                            methodChannel.invokeMethod("onDismiss", null)
                        }
                )

        nativePaywallView.setPaywallListener(
                object : PaywallListenerWrapper() {
                    override fun onPurchaseStarted(rcPackage: Map<String, Any?>) {
                        Log.d(TAG, "onPurchaseStarted — package=${rcPackage["identifier"]}")
                        methodChannel.invokeMethod("onPurchaseStarted", rcPackage)
                    }
                    override fun onPurchaseCompleted(
                            customerInfo: Map<String, Any?>,
                            storeTransaction: Map<String, Any?>
                    ) {
                        Log.d(
                                TAG,
                                "onPurchaseCompleted — transaction=${storeTransaction["transactionIdentifier"]}"
                        )
                        methodChannel.invokeMethod(
                                "onPurchaseCompleted",
                                mapOf(
                                        "customerInfo" to customerInfo,
                                        "storeTransaction" to storeTransaction
                                )
                        )
                    }
                    override fun onPurchaseCancelled() {
                        Log.d(TAG, "onPurchaseCancelled")
                        methodChannel.invokeMethod("onPurchaseCancelled", null)
                    }
                    override fun onPurchaseError(error: Map<String, Any?>) {
                        Log.e(TAG, "onPurchaseError — error=$error")
                        methodChannel.invokeMethod("onPurchaseError", error)
                    }
                    override fun onRestoreCompleted(customerInfo: Map<String, Any?>) {
                        Log.d(TAG, "onRestoreCompleted")
                        methodChannel.invokeMethod("onRestoreCompleted", customerInfo)
                    }
                    override fun onRestoreError(error: Map<String, Any?>) {
                        Log.e(TAG, "onRestoreError — error=$error")
                        methodChannel.invokeMethod("onRestoreError", error)
                    }
                }
        )

        nativePaywallView.setOfferingId(offeringIdentifier)
        Log.d(TAG, "PaywallView id=$id initialised successfully")
    }

    override fun onMethodCall(methodCall: MethodCall, result: MethodChannel.Result) {
        Log.d(TAG, "onMethodCall: method=${methodCall.method}")
        when (methodCall.method) {
            else -> result.notImplemented()
        }
    }
}
