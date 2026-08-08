import 'package:flutter/material.dart';
import 'package:flutter_web_plugins/url_strategy.dart';
import 'app.dart';
import 'core/auth_service.dart';

void main() {
  usePathUrlStrategy();
  runApp(const BootstrapApp());
}

class BootstrapApp extends StatefulWidget {
  const BootstrapApp({super.key});
  @override State<BootstrapApp> createState() => _BootstrapAppState();
}

class _BootstrapAppState extends State<BootstrapApp> {
  late final Future<AuthService> initialization = AuthService.initialize();
  @override Widget build(BuildContext context) => FutureBuilder<AuthService>(future: initialization, builder: (context, snapshot) {
    if (snapshot.hasError) return const MaterialApp(home: Scaffold(body: Center(child: Text('The application could not start. Check the connection and reload.'))));
    if (!snapshot.hasData) return const MaterialApp(home: Scaffold(body: Center(child: CircularProgressIndicator())));
    return SeatBiddingApp(auth: snapshot.data!);
  });
}
