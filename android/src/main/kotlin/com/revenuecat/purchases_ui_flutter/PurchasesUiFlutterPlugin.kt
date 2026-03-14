package com.revenuecat.purchases_ui_flutter

import android.app.Activity
import android.content.Intent
import android.util.Log
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.hybridcommon.ui.PaywallResultListener
import com.revenuecat.purchases.hybridcommon.ui.PaywallSource
import com.revenuecat.purchases.hybridcommon.ui.PresentPaywallOptions
import com.revenuecat.purchases.hybridcommon.ui.presentPaywallFromFragment
import com.revenuecat.purchases.ui.revenuecatui.customercenter.ShowCustomerCenter
import com.revenuecat.purchases_ui_flutter.views.PaywallFooterViewFactory
import com.revenuecat.purchases_ui_flutter.views.PaywallViewFactory
import io.flutter.embedding.android.FlutterFragmentActivity
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import java.util.Locale

class PurchasesUiFlutterPlugin :
        FlutterPlugin, MethodCallHandler, ActivityAware, PluginRegistry.ActivityResultListener {
    private val TAG = "PurchasesUIFlutter"

    private var activity: Activity? = null

    private lateinit var channel: MethodChannel

    private var pendingResult: Result? = null

    companion object {
        private const val REQUEST_CODE_CUSTOMER_CENTER = 1001
    }

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        Log.d(TAG, "onAttachedToEngine: registering platform view factories")
        flutterPluginBinding.platformViewRegistry.registerViewFactory(
                "com.revenuecat.purchasesui/PaywallView",
                PaywallViewFactory(flutterPluginBinding.binaryMessenger) { activity }
        )
        flutterPluginBinding.platformViewRegistry.registerViewFactory(
                "com.revenuecat.purchasesui/PaywallFooterView",
                PaywallFooterViewFactory(flutterPluginBinding.binaryMessenger) { activity }
        )
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "purchases_ui_flutter")
        channel.setMethodCallHandler(this)
        Log.d(TAG, "onAttachedToEngine: setup complete")
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        Log.d(TAG, "onMethodCall: method=${call.method}")
        when (call.method) {
            "presentPaywall" ->
                    presentPaywall(
                            result = result,
                            requiredEntitlementIdentifier = null,
                            offeringIdentifier = call.argument("offeringIdentifier"),
                            displayCloseButton = call.argument("displayCloseButton"),
                            locale = call.argument("locale"),
                    )
            "presentPaywallIfNeeded" -> {
                val requiredEntitlementIdentifier: String? =
                        call.argument("requiredEntitlementIdentifier")
                val offeringIdentifier: String? = call.argument("offeringIdentifier")
                val displayCloseButton: Boolean? = call.argument("displayCloseButton")
                val locale: String? = call.argument("locale")
                presentPaywall(
                        result = result,
                        requiredEntitlementIdentifier = requiredEntitlementIdentifier,
                        offeringIdentifier = offeringIdentifier,
                        displayCloseButton = displayCloseButton,
                        locale = locale,
                )
            }
            "presentCustomerCenter" ->
                    presentCustomerCenter(
                            result = result,
                    )
            else -> {
                result.notImplemented()
            }
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        Log.d(TAG, "onDetachedFromEngine")
        channel.setMethodCallHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        Log.d(TAG, "onAttachedToActivity: activity=${binding.activity::class.simpleName}")
        activity = binding.activity
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        Log.d(TAG, "onDetachedFromActivityForConfigChanges")
        onDetachedFromActivity()
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        Log.d(
                TAG,
                "onReattachedToActivityForConfigChanges: activity=${binding.activity::class.simpleName}"
        )
        onAttachedToActivity(binding)
    }

    override fun onDetachedFromActivity() {
        Log.d(TAG, "onDetachedFromActivity")
        activity = null
    }

    private fun presentPaywall(
            result: Result,
            requiredEntitlementIdentifier: String?,
            offeringIdentifier: String?,
            displayCloseButton: Boolean?,
            locale: String?,
    ) {
        Log.d(
                TAG,
                "presentPaywall — offeringIdentifier=$offeringIdentifier, requiredEntitlementIdentifier=$requiredEntitlementIdentifier, displayCloseButton=$displayCloseButton, locale=$locale"
        )

        val activity = getActivityFragment()
        if (activity == null) {
            Log.e(TAG, "presentPaywall failed: activity is not a FlutterFragmentActivity")
            result.error(
                    "PAYWALLS_MISSING_WRONG_ACTIVITY",
                    "Make sure your MainActivity inherits from FlutterFragmentActivity",
                    null
            )
            return
        }

        // Apply locale override so the SDK's getOfferings request is made with the correct locale.
        val originalLocale = Locale.getDefault()
        if (!locale.isNullOrBlank()) {
            val parsed = Locale.forLanguageTag(locale.replace("_", "-"))
            if (parsed.language.isNotEmpty()) {
                Locale.setDefault(parsed)
                Log.d(TAG, "presentPaywall: Locale.setDefault → $parsed (was $originalLocale)")
            }
        }

        Log.d(TAG, "presentPaywall: fetching fresh offerings before presenting")
        Purchases.sharedInstance.getOfferingsWith(
                onError = { error ->
                    // Fetch failed — still present using cached data.
                    Log.e(
                            TAG,
                            "presentPaywall: getOfferings error: ${error.message} — presenting anyway"
                    )
                    presentPaywallFromFragment(
                            activity,
                            PresentPaywallOptions(
                                    paywallSource =
                                            offeringIdentifier?.let {
                                                PaywallSource.OfferingIdentifier(it)
                                            }
                                                    ?: PaywallSource.DefaultOffering,
                                    requiredEntitlementIdentifier = requiredEntitlementIdentifier,
                                    shouldDisplayDismissButton = displayCloseButton,
                                    paywallResultListener =
                                            object : PaywallResultListener {
                                                override fun onPaywallResult(
                                                        paywallResult: String
                                                ) {
                                                    Log.d(TAG, "onPaywallResult: $paywallResult")
                                                    Locale.setDefault(originalLocale)
                                                    result.success(paywallResult)
                                                }
                                            }
                            )
                    )
                },
                onSuccess = { offerings ->
                    Log.d(TAG, "presentPaywall: getOfferings success, presenting paywall")
                    val resolvedId =
                            if (offeringIdentifier != null) {
                                val found = offerings.all[offeringIdentifier]
                                if (found == null)
                                        Log.w(
                                                TAG,
                                                "presentPaywall: offering '$offeringIdentifier' not found, using default"
                                        )
                                found?.identifier
                                        ?: offerings.current?.identifier ?: offeringIdentifier
                            } else {
                                offerings.current?.identifier
                            }
                    Log.d(TAG, "presentPaywall: resolved offeringId=$resolvedId")
                    presentPaywallFromFragment(
                            activity,
                            PresentPaywallOptions(
                                    paywallSource =
                                            resolvedId?.let { PaywallSource.OfferingIdentifier(it) }
                                                    ?: PaywallSource.DefaultOffering,
                                    requiredEntitlementIdentifier = requiredEntitlementIdentifier,
                                    shouldDisplayDismissButton = displayCloseButton,
                                    paywallResultListener =
                                            object : PaywallResultListener {
                                                override fun onPaywallResult(
                                                        paywallResult: String
                                                ) {
                                                    Log.d(TAG, "onPaywallResult: $paywallResult")
                                                    Locale.setDefault(originalLocale)
                                                    Log.d(
                                                            TAG,
                                                            "presentPaywall: Locale.setDefault restored → $originalLocale"
                                                    )
                                                    result.success(paywallResult)
                                                }
                                            }
                            )
                    )
                }
        )
    }

    private fun presentCustomerCenter(
            result: Result,
    ) {
        Log.d(TAG, "presentCustomerCenter called")
        activity?.let {
            Log.d(TAG, "presentCustomerCenter: starting activity")
            pendingResult = result
            presentCustomerCenterFromActivity(it)
        }
                ?: run {
                    Log.e(TAG, "presentCustomerCenter failed: no activity available")
                    result.error(
                            "CUSTOMER_CENTER_MISSING_ACTIVITY",
                            "Could not present Customer Center. There's no activity",
                            null
                    )
                }
    }

    private fun presentCustomerCenterFromActivity(activity: Activity) {
        val intent = ShowCustomerCenter().createIntent(activity, Unit)
        activity.startActivityForResult(intent, REQUEST_CODE_CUSTOMER_CENTER)
    }

    private fun getActivityFragment(): FlutterFragmentActivity? {
        val activity: Activity? = this.activity
        return if (activity is FlutterFragmentActivity) {
            activity
        } else {
            Log.e(TAG, "Paywalls require your activity to subclass FlutterFragmentActivity")
            null
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode == REQUEST_CODE_CUSTOMER_CENTER) {
            if (resultCode == Activity.RESULT_OK) {
                Log.d(TAG, "Customer Center closed successfully")
                pendingResult?.success("Customer Center closed successfully")
            } else {
                Log.d(TAG, "Customer Center closed with result code: $resultCode")
                pendingResult?.error(
                        "CUSTOMER_CENTER_ERROR",
                        "Customer Center closed with result code: $resultCode",
                        null
                )
            }
            pendingResult = null
            return true
        }
        return false
    }
}
