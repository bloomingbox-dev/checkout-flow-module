import ExpoModulesCore
import SwiftUI
import CheckoutComponentsSDK

public class CheckoutModule: Module {
    private var checkoutComponents: CheckoutComponents?
    private var publicKey = ""
    private var environment = CheckoutComponents.Environment.sandbox

    private var hostingController: UIHostingController<AnyView>?
    private var rootViewController: UIViewController?

  public func definition() -> ModuleDefinition {
    Name("CheckoutModule")

    Events("onSuccess", "onFail")
      
    AsyncFunction("setCredentials") { (options: [String: Any]) in
        try await self.setCredentials(options: options as NSDictionary)
    }

    AsyncFunction("initializeCheckout") { (paymentSession: [String: Any]) in
      try await self.initialize(paymentSession: paymentSession as NSDictionary)
    }

    AsyncFunction("renderFlow") {
      try await MainActor.run {
        try self.renderFlow()
      }
    }
  }
    
  func setCredentials (options: NSDictionary) {
      guard let publicKey = options["publicKey"] as? String,
            let environment = options["environment"] as? String else {
          print("Missing or invalid parameters")
          return
      }

      self.publicKey = publicKey

      switch environment {
      case "production":
          self.environment = CheckoutComponents.Environment.production
      case "sandbox":
          self.environment = CheckoutComponents.Environment.sandbox
      default:
          print("Invalid environment value")
      }
  }

  func initialize(paymentSession: NSDictionary) async throws {
    guard let paymentSessionID = paymentSession["id"] as? String,
          let paymentSessionSecret = paymentSession["payment_session_secret"] as? String else {
      throw NSError(domain: "InvalidSession", code: 400, userInfo: nil)
    }

    let session = PaymentSession(id: paymentSessionID, paymentSessionSecret: paymentSessionSecret)

    let config = try await CheckoutComponents.Configuration(
      paymentSession: session,
      publicKey: publicKey,
      environment: environment,
      callbacks: CheckoutComponents.Callbacks(
        onSuccess: { paymentMethod, paymentID in
            print("Payment successful: \(paymentID)");
            self.sendEvent("onSuccess", ["paymentID": paymentID]);
            Task { @MainActor in
                        self.closeFlowView()
                    }
        },
        onError: { error in
            print("Error: \(error)");
            self.sendEvent("onFail", ["error": error]);
        }
      )
    )

    self.checkoutComponents = CheckoutComponents(configuration: config)
  }

  @MainActor
  func renderFlow() throws {
    guard let checkoutComponents = checkoutComponents else {
      throw NSError(domain: "NotInitialized", code: 500, userInfo: nil)
    }

    guard let rootViewController = UIApplication.shared.connectedScenes
      .compactMap({ $0 as? UIWindowScene })
      .flatMap({ $0.windows })
      .first(where: { $0.isKeyWindow })?.rootViewController else {
      throw NSError(domain: "NoRootView", code: 500, userInfo: nil)
    }

    let flowComponent = try checkoutComponents.create(.flow())
    let flowView = flowComponent.render()

    let hostingController = UIHostingController(rootView: flowView)
    self.hostingController = hostingController

    hostingController.overrideUserInterfaceStyle = .light
    hostingController.view.backgroundColor = .white
    hostingController.view.subviews.first?.backgroundColor = .white

    if let sheet = hostingController.sheetPresentationController {
        sheet.detents = [.medium(), .large()]
        sheet.prefersGrabberVisible = true
        sheet.prefersScrollingExpandsWhenScrolledToEdge = false
        sheet.preferredCornerRadius = 24
    }

    self.rootViewController = rootViewController
    rootViewController.present(hostingController, animated: true)
  }
    
  @MainActor
    private func closeFlowView() {
        guard let rootViewController = self.rootViewController else { return }
        guard let hostingController = self.hostingController else { return }

        rootViewController.dismiss(animated: true)

        hostingController.willMove(toParent: nil)
        hostingController.view.removeFromSuperview()
        hostingController.removeFromParent()
        self.hostingController = nil
    }
}
