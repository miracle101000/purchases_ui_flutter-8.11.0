import Flutter
import PurchasesHybridCommonUI
import RevenueCatUI
import UIKit

class PurchasesUiPaywallViewFactory: NSObject, FlutterPlatformViewFactory {
  private var messenger: FlutterBinaryMessenger

  init(messenger: FlutterBinaryMessenger) {
    self.messenger = messenger
    super.init()
  }

  func create(
    withFrame frame: CGRect,
    viewIdentifier viewId: Int64,
    arguments args: Any?
  ) -> FlutterPlatformView {
    if #available(iOS 15.0, *) {
      return PurchasesUiPaywallView(
        frame: frame,
        viewIdentifier: viewId,
        arguments: args,
        binaryMessenger: messenger)
    } else {
      print("Error: attempted to present paywalls on unsupported iOS version.")
      return UnsupportedPlatformView()
    }
  }

  public func createArgsCodec() -> FlutterMessageCodec & NSObjectProtocol {
    return FlutterStandardMessageCodec.sharedInstance()
  }
}

@available(iOS 15.0, *)
final class PurchasesUiViewControllerWrapper<T: UIViewController>: UIView {
  private var wrappedViewController: T
  private var addedToHierarchy = false
  var userInterfaceStyle: UIUserInterfaceStyle = .unspecified

  init(viewController: T) {
    self.wrappedViewController = viewController
    super.init(frame: .zero)
  }

  required init?(coder: NSCoder) {
    fatalError("init(coder:) has not been implemented")
  }

  override func layoutSubviews() {
    super.layoutSubviews()
    if !addedToHierarchy, let parentController = parentViewController {
      wrappedViewController.view.translatesAutoresizingMaskIntoConstraints = false
      parentController.addChild(wrappedViewController)
      addSubview(wrappedViewController.view)
      wrappedViewController.didMove(toParent: parentController)
      wrappedViewController.view.overrideUserInterfaceStyle = userInterfaceStyle

      NSLayoutConstraint.activate([
        wrappedViewController.view.topAnchor.constraint(equalTo: safeAreaLayoutGuide.topAnchor),
        wrappedViewController.view.bottomAnchor.constraint(
          equalTo: safeAreaLayoutGuide.bottomAnchor),
        wrappedViewController.view.leadingAnchor.constraint(
          equalTo: safeAreaLayoutGuide.leadingAnchor),
        wrappedViewController.view.trailingAnchor.constraint(
          equalTo: safeAreaLayoutGuide.trailingAnchor),
      ])
      addedToHierarchy = true
    }
  }
}

@available(iOS 15.0, *)
class PurchasesUiPaywallView: NSObject, FlutterPlatformView {
  private var _view: PurchasesUiViewControllerWrapper<PaywallViewController>
  private var _paywallProxy: PaywallProxy?
  private var _methodChannel: FlutterMethodChannel
  private var _paywallViewController: PaywallViewController

  init(
    frame: CGRect,
    viewIdentifier viewId: Int64,
    arguments args: Any?,
    binaryMessenger messenger: FlutterBinaryMessenger
  ) {
    _methodChannel = FlutterMethodChannel(
      name: "com.revenuecat.purchasesui/PaywallView/\(viewId)",
      binaryMessenger: messenger)
    let paywallProxy = PaywallProxy()
    _paywallProxy = paywallProxy
    _paywallViewController = paywallProxy.createPaywallView()

    _view = PurchasesUiViewControllerWrapper(viewController: _paywallViewController)

    if let args = args as? [String: Any?] {
      if let offeringId = args["offeringIdentifier"] as? String {
        _paywallViewController.update(with: offeringId)
      }
      if let displayCloseButton = args["displayCloseButton"] as? Bool {
        _paywallViewController.update(with: displayCloseButton)
      }
      if let theme = args["theme"] as? String {
        _view.userInterfaceStyle = theme == "dark" ? .dark : .light
      }
    }

    super.init()
    _paywallProxy?.delegate = self
    setupMethodCallHandler()
  }

  func view() -> UIView {
    return _view
  }

  private func setupMethodCallHandler() {
    _methodChannel.setMethodCallHandler { [weak self] (call, result) in
      guard self != nil else { return }
      switch call.method {
      default:
        result(FlutterMethodNotImplemented)
      }
    }
  }
}

@available(iOS 15.0, *)
extension PurchasesUiPaywallView: PaywallViewControllerDelegateWrapper {
  func paywallViewController(
    _ controller: PaywallViewController,
    didStartPurchaseWith packageDictionary: [String: Any]
  ) {
    _methodChannel.invokeMethod("onPurchaseStarted", arguments: packageDictionary)
  }

  func paywallViewController(
    _ controller: PaywallViewController,
    didFinishPurchasingWith customerInfoDictionary: [String: Any],
    transaction transactionDictionary: [String: Any]?
  ) {
    _methodChannel.invokeMethod(
      "onPurchaseCompleted",
      arguments: [
        "customerInfo": customerInfoDictionary,
        "storeTransaction": transactionDictionary,
      ])
  }

  func paywallViewControllerDidCancelPurchase(_ controller: PaywallViewController) {
    _methodChannel.invokeMethod("onPurchaseCancelled", arguments: nil)
  }

  func paywallViewController(
    _ controller: PaywallViewController,
    didFailPurchasingWith errorDictionary: [String: Any]
  ) {
    _methodChannel.invokeMethod("onPurchaseError", arguments: errorDictionary)
  }

  func paywallViewController(
    _ controller: PaywallViewController,
    didFinishRestoringWith customerInfoDictionary: [String: Any]
  ) {
    _methodChannel.invokeMethod("onRestoreCompleted", arguments: customerInfoDictionary)
  }

  func paywallViewController(
    _ controller: PaywallViewController,
    didFailRestoringWith errorDictionary: [String: Any]
  ) {
    _methodChannel.invokeMethod("onRestoreError", arguments: errorDictionary)
  }

  func paywallViewControllerRequestedDismissal(_ controller: PaywallViewController) {
    _methodChannel.invokeMethod("onDismiss", arguments: nil)
  }
}
