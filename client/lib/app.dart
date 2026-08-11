import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'core/api_client.dart';
import 'core/auth_service.dart';
import 'features/assignments/assignments_screen.dart';
import 'features/admin/seat_reservations_screen.dart';
import 'features/auth/activation_code_screen.dart';
import 'features/auth/create_password_screen.dart';
import 'features/auth/login_screen.dart';
import 'features/bidding/bids_screen.dart';
import 'features/help/help_screen.dart';

class SeatBiddingApp extends StatefulWidget {
  const SeatBiddingApp({super.key, required this.auth});
  final AuthService auth;
  @override
  State<SeatBiddingApp> createState() => _SeatBiddingAppState();
}

class _SeatBiddingAppState extends State<SeatBiddingApp> {
  late final ApiClient api = ApiClient(widget.auth);
  late final GoRouter router = GoRouter(
    initialLocation: '/assignments',
    refreshListenable: widget.auth,
    redirect: (context, state) {
      final path = state.uri.path;
      if (widget.auth.isAuthenticated) {
        return path.startsWith('/login') || path.startsWith('/activate')
            ? '/assignments'
            : null;
      }
      if (path == '/login') return null;
      if (path == '/activate/code' && widget.auth.email != null) return null;
      if (path == '/activate/password' && widget.auth.activationToken != null) {
        return null;
      }
      widget.auth.rememberIntendedRoute(path);
      return '/login';
    },
    routes: [
      GoRoute(
        path: '/login',
        builder: (context, state) => LoginScreen(auth: widget.auth),
      ),
      GoRoute(
        path: '/activate/code',
        builder: (context, state) => ActivationCodeScreen(auth: widget.auth),
      ),
      GoRoute(
        path: '/activate/password',
        builder: (context, state) => CreatePasswordScreen(auth: widget.auth),
      ),
      ShellRoute(
        builder: (context, state, child) =>
            AppShell(auth: widget.auth, location: state.uri.path, child: child),
        routes: [
          GoRoute(
            path: '/assignments',
            builder: (context, state) => AssignmentsScreen(api: api),
          ),
          GoRoute(
            path: '/bids',
            builder: (context, state) => BidsScreen(api: api),
          ),
          GoRoute(
            path: '/help',
            builder: (context, state) => const HelpScreen(),
          ),
          if (kIsWeb)
            GoRoute(
              path: '/admin/reservations',
              builder: (context, state) =>
                  SeatReservationAdminGate(auth: widget.auth, api: api),
            ),
        ],
      ),
    ],
  );

  @override
  Widget build(BuildContext context) => MaterialApp.router(
    title: 'Office seats',
    debugShowCheckedModeBanner: false,
    routerConfig: router,
    theme: ThemeData(
      colorSchemeSeed: const Color(0xff315c4b),
      brightness: Brightness.light,
      fontFamily: 'Roboto',
      useMaterial3: true,
      visualDensity: VisualDensity.standard,
    ),
  );
}

class AppShell extends StatelessWidget {
  const AppShell({
    super.key,
    required this.auth,
    required this.location,
    required this.child,
    bool? isWeb,
  }) : isWeb = isWeb ?? kIsWeb;
  final AuthService auth;
  final String location;
  final Widget child;
  final bool isWeb;
  int get selected => location.startsWith('/admin')
      ? 2
      : location.startsWith('/bids')
      ? 1
      : 0;
  void navigate(BuildContext context, int index) => context.go(
    index == 0
        ? '/assignments'
        : index == 1
        ? '/bids'
        : '/admin/reservations',
  );

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) {
      final wide = constraints.maxWidth >= 800;
      final showAdmin = wide && isWeb && auth.user?.isAdmin == true;
      final promotion =
          isWeb &&
          defaultTargetPlatform == TargetPlatform.android &&
          auth.configuration.androidDownloadUrl != null;
      return Scaffold(
        appBar: AppBar(
          title: const Text('Office seats'),
          actions: [
            PopupMenuButton<String>(
              tooltip: 'Menu',
              onSelected: (value) async {
                if (value == 'help' && context.mounted) context.go('/help');
                if (value == 'android' && context.mounted) {
                  showDialog<void>(
                    context: context,
                    builder: (_) => AlertDialog(
                      title: const Text('Get the Android app'),
                      content: SelectableText(
                        auth.configuration.androidDownloadUrl!,
                      ),
                      actions: [
                        TextButton(
                          onPressed: () => Navigator.pop(context),
                          child: const Text('Close'),
                        ),
                      ],
                    ),
                  );
                }
                if (value == 'logout') {
                  await auth.logout();
                  if (context.mounted) context.go('/login');
                }
              },
              itemBuilder: (_) => [
                const PopupMenuItem(value: 'help', child: Text('Help')),
                if (promotion)
                  const PopupMenuItem(
                    value: 'android',
                    child: Text('Get the Android app'),
                  ),
                const PopupMenuItem(value: 'logout', child: Text('Log out')),
              ],
            ),
          ],
        ),
        body: wide
            ? Row(
                children: [
                  NavigationRail(
                    selectedIndex: showAdmin || selected < 2 ? selected : 0,
                    onDestinationSelected: (index) => navigate(context, index),
                    labelType: NavigationRailLabelType.all,
                    destinations: [
                      const NavigationRailDestination(
                        icon: Icon(Icons.event_seat),
                        label: Text('Seat assignments'),
                      ),
                      const NavigationRailDestination(
                        icon: Icon(Icons.gavel),
                        label: Text('Place bids'),
                      ),
                      if (showAdmin)
                        const NavigationRailDestination(
                          icon: Icon(Icons.admin_panel_settings),
                          label: Text('Admin'),
                        ),
                    ],
                  ),
                  const VerticalDivider(width: 1),
                  Expanded(child: child),
                ],
              )
            : child,
        bottomNavigationBar: wide
            ? null
            : NavigationBar(
                selectedIndex: selected < 2 ? selected : 0,
                onDestinationSelected: (index) => navigate(context, index),
                destinations: const [
                  NavigationDestination(
                    icon: Icon(Icons.event_seat),
                    label: 'Seat assignments',
                  ),
                  NavigationDestination(
                    icon: Icon(Icons.gavel),
                    label: 'Place bids',
                  ),
                ],
              ),
      );
    },
  );
}
