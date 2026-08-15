import Flutter
import UIKit

@main
@objc class AppDelegate: FlutterAppDelegate, FlutterImplicitEngineDelegate {
  // One shared handler backs both channels, so the one-shot MethodChannel
  // call and the live EventChannel stream read from the same NWPathMonitor
  // instance instead of the method channel returning a hardcoded guess.
  private let signalHandler = SignalStreamHandler()

  override func application(
    _ application: UIApplication,
    didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
  ) -> Bool {
    let controller : FlutterViewController = window?.rootViewController as! FlutterViewController

    let methodChannel = FlutterMethodChannel(name: "com.fluttercon/signal",
                                              binaryMessenger: controller.binaryMessenger)
    methodChannel.setMethodCallHandler({ [weak self]
      (call: FlutterMethodCall, result: @escaping FlutterResult) -> Void in
      if call.method == "getSignalStrength" {
        result(self?.signalHandler.getSignalInfo() ?? ["type": "none", "level": 0])
      } else {
        result(FlutterMethodNotImplemented)
      }
    })

    let eventChannel = FlutterEventChannel(name: "com.fluttercon/signal_stream",
                                            binaryMessenger: controller.binaryMessenger)
    eventChannel.setStreamHandler(signalHandler)

    GeneratedPluginRegistrant.register(with: self)
    return super.application(application, didFinishLaunchingWithOptions: launchOptions)
  }

  func didInitializeImplicitFlutterEngine(_ engineBridge: FlutterImplicitEngineBridge) {
    GeneratedPluginRegistrant.register(with: engineBridge.pluginRegistry)
  }
}
