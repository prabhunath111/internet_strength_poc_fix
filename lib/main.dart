import 'package:flutter/material.dart';
import 'package:internet_strentgh_poc/signal_badge.dart';
import 'package:internet_strentgh_poc/signal_info.dart';
import 'package:internet_strentgh_poc/signal_strength_service.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Signal Strength POC',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      home: const MyHomePage(title: 'Signal Strength POC'),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key, required this.title});

  final String title;

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        title: Text(widget.title),
        actions: const [
          Padding(
            padding: EdgeInsets.symmetric(horizontal: 16.0),
            child: SignalBadge(),
          ),
        ],
      ),
      body: Center(
        child: StreamBuilder<SignalInfo>(
          stream: SignalStrengthService.watch(),
          builder: (context, snapshot) {
            if (snapshot.hasError) {
              return Text('Error: ${snapshot.error}');
            }
            if (!snapshot.hasData) {
              return Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const CircularProgressIndicator(),
                  const SizedBox(height: 16),
                  const Text("Waiting for signal data..."),
                  const SizedBox(height: 16),
                  ElevatedButton(
                    onPressed: () => _manualRefresh(context),
                    child: const Text("Manual Refresh"),
                  ),
                ],
              );
            }

            final info = snapshot.data!;
            return Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(
                  info.type == 'wifi' ? Icons.wifi : Icons.signal_cellular_alt,
                  size: 64,
                  color: _colorForLevel(info.level),
                ),
                const SizedBox(height: 16),
                Text(
                  'Type: ${info.type.toUpperCase()}',
                  style: Theme.of(context).textTheme.headlineSmall,
                ),
                Text(
                  'Level: ${info.level} / 4',
                  style: Theme.of(context).textTheme.bodyLarge,
                ),
                if (info.rssi != null)
                  Text(
                    'RSSI: ${info.rssi} dBm',
                    style: Theme.of(context).textTheme.bodyMedium,
                  ),
                const SizedBox(height: 8),
                Text(
                  'Quality: ${info.quality.name.toUpperCase()}',
                  style: TextStyle(
                    fontWeight: FontWeight.bold,
                    color: _colorForLevel(info.level),
                  ),
                ),
                const SizedBox(height: 24),
                OutlinedButton(
                  onPressed: () => _manualRefresh(context),
                  child: const Text("Manual Refresh"),
                ),
              ],
            );
          },
        ),
      ),
    );
  }

  /// Triggers the one-shot MethodChannel read and surfaces the result
  /// directly, instead of fetching data and discarding it (the live
  /// StreamBuilder above already keeps the main display current).
  Future<void> _manualRefresh(BuildContext context) async {
    final messenger = ScaffoldMessenger.of(context);
    try {
      final info = await SignalStrengthService.current();
      messenger.showSnackBar(
        SnackBar(
          content: Text(
            'Manual refresh: ${info.type.toUpperCase()} · '
            'level ${info.level}/4'
            '${info.rssi != null ? ' · ${info.rssi} dBm' : ''}',
          ),
          duration: const Duration(seconds: 3),
        ),
      );
    } catch (e) {
      messenger.showSnackBar(
        SnackBar(content: Text('Manual refresh failed: $e')),
      );
    }
  }

  Color _colorForLevel(int level) {
    switch (level) {
      case 0:
      case 1:
        return Colors.red;
      case 2:
        return Colors.orange;
      case 3:
        return Colors.blue;
      case 4:
        return Colors.green;
      default:
        return Colors.grey;
    }
  }
}
