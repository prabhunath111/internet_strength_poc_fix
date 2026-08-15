import Flutter
import UIKit
import Network

/// Single shared instance used for BOTH the MethodChannel and the
/// EventChannel (see AppDelegate). The underlying NWPathMonitor runs
/// continuously from init(), independent of whether Dart currently has an
/// active EventChannel subscription -- so a one-shot "getSignalStrength"
/// call can answer with the real, current path instead of a guess.
///
/// iOS has no public API for graduated WiFi RSSI or cellular bar count
/// (CoreTelephony's old signal-strength fields were deprecated and zeroed
/// out for privacy years ago). What we CAN report reliably is connectivity
/// + interface type, so `level` here is intentionally binary: 4 when
/// connected, 0 when not. That's a platform limitation, not a shortcut.
final class SignalStreamHandler: NSObject, FlutterStreamHandler {
    private var eventSink: FlutterEventSink?
    private let monitor = NWPathMonitor()
    private let queue = DispatchQueue(label: "com.fluttercon.signalMonitor")

    private let lock = NSLock()
    private var latestPath: NWPath?

    override init() {
        super.init()
        monitor.pathUpdateHandler = { [weak self] path in
            self?.cache(path)
            self?.pushIfListening(path)
        }
        monitor.start(queue: queue)
    }

    deinit {
        monitor.cancel()
    }

    private func cache(_ path: NWPath) {
        lock.lock()
        latestPath = path
        lock.unlock()
    }

    private func currentPath() -> NWPath? {
        lock.lock()
        defer { lock.unlock() }
        return latestPath
    }

    private func pushIfListening(_ path: NWPath) {
        guard let sink = eventSink else { return }
        let data = Self.info(from: path)
        DispatchQueue.main.async {
            sink(data)
        }
    }

    // MARK: FlutterStreamHandler

    func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
        eventSink = events
        // Emit the currently-known path immediately so the first frame isn't
        // blank while waiting for the next OS-driven path update.
        if let path = currentPath() {
            pushIfListening(path)
        }
        return nil
    }

    func onCancel(withArguments arguments: Any?) -> FlutterError? {
        eventSink = nil
        return nil
    }

    // MARK: One-shot read (MethodChannel)

    /// Real synchronous-ish snapshot for the "getSignalStrength" method call.
    /// Previously this was a hardcoded stub that always reported "connected,
    /// full bars" regardless of actual state.
    func getSignalInfo() -> [String: Any] {
        guard let path = currentPath() else {
            return ["type": "none", "level": 0]
        }
        return Self.info(from: path)
    }

    private static func info(from path: NWPath) -> [String: Any] {
        var type = "none"
        var level = 0

        if path.status == .satisfied {
            level = 4
            if path.usesInterfaceType(.wifi) {
                type = "wifi"
            } else if path.usesInterfaceType(.cellular) {
                type = "cellular"
            } else {
                type = "other"
            }
        }

        return ["type": type, "level": level]
    }
}
