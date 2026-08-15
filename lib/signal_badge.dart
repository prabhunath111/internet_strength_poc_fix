// lib/signal_badge.dart
import 'package:flutter/material.dart';
import 'signal_strength_service.dart';
import 'signal_info.dart';

class SignalBadge extends StatelessWidget {
  const SignalBadge({super.key});

  @override
  Widget build(BuildContext context) {
    return StreamBuilder<SignalInfo>(
      stream: SignalStrengthService.watch(),
      builder: (context, snap) {
        final level = snap.data?.level ?? 0;
        return Row(
          mainAxisSize: MainAxisSize.min,
          children: List.generate(4, (i) {
            return Container(
              width: 4,
              height: 6.0 + i * 5,
              margin: const EdgeInsets.only(right: 2),
              decoration: BoxDecoration(
                color: i < level ? _colorForLevel(level) : Colors.white24,
                borderRadius: BorderRadius.circular(2),
              ),
            );
          }),
        );
      },
    );
  }

  Color _colorForLevel(int level) {
    switch (level) {
      case 0:
      case 1:
        return Colors.redAccent;
      case 2:
        return Colors.amber;
      case 3:
        return Colors.tealAccent;
      default:
        return Colors.greenAccent;
    }
  }
}