import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../../core/auth_service.dart';
import 'auth_error.dart';
import 'password_validation.dart';

class CreatePasswordScreen extends StatefulWidget {
  const CreatePasswordScreen({super.key, required this.auth});
  final AuthService auth;
  @override State<CreatePasswordScreen> createState() => _CreatePasswordScreenState();
}

class _CreatePasswordScreenState extends State<CreatePasswordScreen> {
  final password = TextEditingController();
  final confirmation = TextEditingController();
  bool busy = false; String? error;
  @override void dispose() { password.dispose(); confirmation.dispose(); super.dispose(); }

  Future<void> submit() async {
    final validation = validateNewPassword(password.text, confirmation.text);
    if (validation != null) { setState(() => error = validation); return; }
    setState(() { busy = true; error = null; });
    try {
      await widget.auth.createPassword(password.text, confirmation.text);
      password.clear(); confirmation.clear();
      if (mounted) context.go('/assignments');
    }
    catch (failure) { if (mounted) setState(() => error = authenticationError(failure)); }
    finally { if (mounted) setState(() => busy = false); }
  }

  @override Widget build(BuildContext context) => Scaffold(body: Center(child: SingleChildScrollView(padding: const EdgeInsets.all(24),
    child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 460), child: Card(child: Padding(padding: const EdgeInsets.all(32),
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, mainAxisSize: MainAxisSize.min, children: [
        Text('Create your password', style: Theme.of(context).textTheme.headlineSmall), const SizedBox(height: 12),
        const Text('Use 15–128 characters. Spaces and Unicode characters are allowed. Common or compromised passwords are rejected.'),
        const SizedBox(height: 16), TextField(controller: password, obscureText: true,
          autofillHints: const [AutofillHints.newPassword], decoration: const InputDecoration(labelText: 'Password', border: OutlineInputBorder())),
        const SizedBox(height: 12), TextField(controller: confirmation, obscureText: true,
          autofillHints: const [AutofillHints.newPassword], onSubmitted: (_) => submit(),
          decoration: const InputDecoration(labelText: 'Confirm password', border: OutlineInputBorder())),
        if (error != null) Padding(padding: const EdgeInsets.only(top: 12), child: Semantics(liveRegion: true,
          child: Text(error!, style: TextStyle(color: Theme.of(context).colorScheme.error)))),
        const SizedBox(height: 20), FilledButton(onPressed: busy ? null : submit, child: const Text('Create password and sign in')),
      ])))))));
}
