import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:go_router/go_router.dart';
import '../../core/auth_service.dart';
import 'auth_error.dart';

class ActivationCodeScreen extends StatefulWidget {
  const ActivationCodeScreen({super.key, required this.auth});
  final AuthService auth;
  @override State<ActivationCodeScreen> createState() => _ActivationCodeScreenState();
}

class _ActivationCodeScreenState extends State<ActivationCodeScreen> {
  final code = TextEditingController();
  Timer? timer;
  bool busy = false;
  String? error;
  @override void initState() { super.initState(); timer = Timer.periodic(const Duration(seconds: 1), (_) { if (mounted) setState(() {}); }); }
  @override void dispose() { timer?.cancel(); code.dispose(); super.dispose(); }
  bool get canResend => widget.auth.resendAvailableAt == null || !widget.auth.resendAvailableAt!.isAfter(DateTime.now().toUtc());

  Future<void> verify() async {
    if (code.text.length != 6 || busy) return;
    setState(() { busy = true; error = null; });
    try { await widget.auth.verifyCode(code.text); if (mounted) context.go('/activate/password'); }
    catch (failure) { if (mounted) setState(() => error = authenticationError(failure)); }
    finally { if (mounted) setState(() => busy = false); }
  }

  Future<void> resend() async {
    setState(() { busy = true; error = null; });
    try { await widget.auth.resend(); code.clear(); }
    catch (failure) { if (mounted) setState(() => error = authenticationError(failure)); }
    finally { if (mounted) setState(() => busy = false); }
  }

  @override Widget build(BuildContext context) => _AuthCard(title: 'Enter activation code', children: [
    Text('We sent a six-digit code to ${widget.auth.email}. It expires after 15 minutes.'), const SizedBox(height: 16),
    TextField(controller: code, keyboardType: TextInputType.number, maxLength: 6,
      inputFormatters: [FilteringTextInputFormatter.digitsOnly], textInputAction: TextInputAction.done,
      onSubmitted: (_) => verify(), decoration: const InputDecoration(labelText: 'Six-digit code', border: OutlineInputBorder())),
    if (error != null) Semantics(liveRegion: true, child: Text(error!, style: TextStyle(color: Theme.of(context).colorScheme.error))),
    const SizedBox(height: 12), FilledButton(onPressed: busy ? null : verify, child: const Text('Verify code')),
    TextButton(onPressed: busy || !canResend ? null : resend, child: Text(canResend ? 'Resend code' : 'Resend available shortly')),
    TextButton(onPressed: busy ? null : () => context.go('/login'), child: const Text('Use a different email')),
  ]);
}

class _AuthCard extends StatelessWidget {
  const _AuthCard({required this.title, required this.children});
  final String title; final List<Widget> children;
  @override Widget build(BuildContext context) => Scaffold(body: Center(child: SingleChildScrollView(padding: const EdgeInsets.all(24),
    child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 440), child: Card(child: Padding(
      padding: const EdgeInsets.all(32), child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, mainAxisSize: MainAxisSize.min,
        children: [Text(title, style: Theme.of(context).textTheme.headlineSmall), const SizedBox(height: 20), ...children])))))));
}
