package com.revenuecat.purchases_ui_flutter.views

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.hybridcommon.ui.PaywallListenerWrapper
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.models.Offerings
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
    private var disposed = false

    override fun getView(): View = nativePaywallView

    override fun dispose() {
        Log.d(TAG, "dispose called for view id=$id — restoring AssetManager locale")
        disposed = true
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

        // Fetch offerings with the locale already set via Locale.setDefault in buildFinalContext.
        // This ensures the SDK requests localised content from the RevenueCat CDN rather than
        // serving a cached response from a previous locale.
        Log.d(TAG, "Fetching fresh offerings for locale=$locale before setting offering")
        Purchases.sharedInstance.getOfferings(
                object : ReceiveOfferingsCallback {
                    override fun onReceived(offerings: Offerings) {
                        if (disposed) {
                            Log.d(
                                    TAG,
                                    "getOfferings callback: view already disposed, skipping setOffering"
                            )
                            return
                        }
                        val offering =
                                if (offeringIdentifier != null) {
                                    offerings.getOffering(offeringIdentifier).also {
                                        if (it == null)
                                                Log.w(
                                                        TAG,
                                                        "Offering '$offeringIdentifier' not found, falling back to current"
                                                )
                                    }
                                            ?: offerings.current
                                } else {
                                    offerings.current
                                }
                        Log.d(TAG, "getOfferings success — using offering: ${offering?.identifier}")
                        if (offering != null) {
                            nativePaywallView.setOffering(offering)
                        } else {
                            Log.w(TAG, "No offering available, falling back to setOfferingId")
                            nativePaywallView.setOfferingId(offeringIdentifier)
                        }
                    }

                    override fun onError(error: PurchasesError) {
                        if (disposed) return
                        Log.e(
                                TAG,
                                "getOfferings error: ${error.message} — falling back to setOfferingId"
                        )
                        nativePaywallView.setOfferingId(offeringIdentifier)
                    }
                }
        )
        Log.d(TAG, "PaywallView id=$id initialised successfully")
    }

    override fun onMethodCall(methodCall: MethodCall, result: MethodChannel.Result) {
        Log.d(TAG, "onMethodCall: method=${methodCall.method}")
        when (methodCall.method) {
            else -> result.notImplemented()
        }
    }
}
