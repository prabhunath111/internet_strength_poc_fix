// lib/signal_strength_service.dart
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:internet_strentgh_poc/signal_info.dart';

class SignalStrengthService {
  static const _method = MethodChannel('com.fluttercon/signal');
  static const _events = EventChannel('com.fluttercon/signal_stream');

  static final Stream<SignalInfo> _broadcastStream = _events
      .receiveBroadcastStream()
      .map((event) => SignalInfo.fromMap(Map<String, dynamic>.from(event)))
      .handleError((error) {
        debugPrint('SignalStrengthService Stream Error: $error');
      });

  /// One-shot read. Throws on failure so callers can distinguish
  /// "no data yet" from "this specific call failed" instead of getting a
  /// silent, indistinguishable level-0 result.
  static Future<SignalInfo> current() async {
    try {
      final result = await _method.invokeMethod<Map>('getSignalStrength');
      return SignalInfo.fromMap(Map<String, dynamic>.from(result!));
    } catch (e) {
      debugPrint('SignalStrengthService Method Error: $e');
      rethrow;
    }
  }

  /// Live stream of updates pushed from native code
  /// Returns a shared broadcast stream to ensure multiple listeners 
  /// (Badge and Body) don't trigger redundant onListen/onCancel calls 
  /// or miss the initial events.
  static Stream<SignalInfo> watch() => _broadcastStream;
}
