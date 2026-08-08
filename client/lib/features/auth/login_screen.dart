import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../core/auth_service.dart';
import '../../core/models.dart';
import 'auth_error.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key, required this.auth});
  final AuthService auth;
  @override State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final email = TextEditingController();
  final password = TextEditingController();
  bool passwordRequired = false;
  bool busy = false;
  String? error;

  @override void dispose() { email.dispose(); password.dispose(); super.dispose(); }

  Future<void> submit() async {
    if (busy) return;
    setState(() { busy = true; error = null; });
    try {
      if (!passwordRequired) {
        final result = await widget.auth.start(email.text);
        if (!mounted) return;
        if (result.nextStep == AuthenticationNextStep.codeRequired) {
          context.go('/activate/code');
        } else {
          setState(() => passwordRequired = true);
        }
      } else {
        await widget.auth.login(password.text);
        if (mounted) context.go(widget.auth.takeIntendedRoute());
      }
    } catch (failure) {
      if (mounted) setState(() => error = authenticationError(failure));
    } finally {
      if (mounted) setState(() => busy = false);
    }
  }

  @override Widget build(BuildContext context) => Scaffold(body: Center(child: SingleChildScrollView(
    padding: const EdgeInsets.all(24), child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 440),
      child: Card(child: Padding(padding: const EdgeInsets.all(32), child: AutofillGroup(child: Column(
        mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          const Icon(Icons.event_seat, size: 64), const SizedBox(height: 16),
          Text(passwordRequired ? 'Enter your password' : 'Office seat bidding',
            textAlign: TextAlign.center, style: Theme.of(context).textTheme.headlineSmall), const SizedBox(height: 20),
          if (!passwordRequired) TextField(controller: email, keyboardType: TextInputType.emailAddress,
            autofillHints: const [AutofillHints.email], textInputAction: TextInputAction.done,
            onSubmitted: (_) => submit(), decoration: const InputDecoration(labelText: 'Email', border: OutlineInputBorder())),
          if (passwordRequired) ...[
            Text(widget.auth.email ?? '', textAlign: TextAlign.center), const SizedBox(height: 12),
            TextField(controller: password, obscureText: true, autofillHints: const [AutofillHints.password],
              textInputAction: TextInputAction.done, onSubmitted: (_) => submit(),
              decoration: const InputDecoration(labelText: 'Password', border: OutlineInputBorder())),
            TextButton(onPressed: busy ? null : () => setState(() { passwordRequired = false; password.clear(); }),
              child: const Text('Use a different email')),
          ],
          if (error != null) Padding(padding: const EdgeInsets.only(top: 12), child: Semantics(liveRegion: true,
            child: Text(error!, style: TextStyle(color: Theme.of(context).colorScheme.error)))),
          const SizedBox(height: 20),
          FilledButton(onPressed: busy ? null : submit, child: busy
            ? const SizedBox.square(dimension: 20, child: CircularProgressIndicator(strokeWidth: 2))
            : Text(passwordRequired ? 'Sign in' : 'Continue')),
        ]))))))));
}
