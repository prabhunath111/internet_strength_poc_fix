// lib/signal_info.dart
enum SignalQuality { none, weak, fair, good, excellent }

class SignalInfo {
  final String type;   // "wifi" or "cellular"
  final int level;     // 0..4 normalized
  final int? rssi;     // dBm, nullable (esp. on iOS WiFi)

  SignalInfo({
    required this.type,
    required this.level,
    this.rssi,
  });

  factory SignalInfo.fromMap(Map<String, dynamic> m) => SignalInfo(
    type: m['type'] as String? ?? "unknown",
    level: m['level'] as int? ?? 0,
    rssi: m['rssi'] as int?,
  );

  SignalQuality get quality => SignalQuality.values[level];
}