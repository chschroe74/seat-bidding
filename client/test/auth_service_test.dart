import 'dart:convert';
import 'dart:typed_data';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:seat_bidding/core/auth_service.dart';
import 'package:seat_bidding/core/managed_cookie_store.dart';

void main() {
  test('restore accepts a valid authentication cookie identity', () async {
    final dio = Dio()..httpClientAdapter = _Adapter(authenticated: true);
    final cookies = _Cookies();
    final auth = AuthService.testing(dio, cookies);

    await auth.restore();

    expect(auth.isAuthenticated, isTrue);
    expect(auth.user?.email, 'alex@example.com');
    expect(cookies.clearCount, 0);
  });

  test('restore clears expired authentication state and refreshes CSRF', () async {
    final dio = Dio()..httpClientAdapter = _Adapter(authenticated: false);
    final cookies = _Cookies();
    final auth = AuthService.testing(dio, cookies);

    await auth.restore();

    expect(auth.isAuthenticated, isFalse);
    expect(auth.csrfToken, 'test-csrf-token');
    expect(cookies.clearCount, 1);
  });
}

class _Cookies implements ManagedCookieStore {
  int clearCount = 0;

  @override
  Future<void> attach(Dio dio) async {}

  @override
  Future<void> clear() async => clearCount++;
}

class _Adapter implements HttpClientAdapter {
  _Adapter({required this.authenticated});
  final bool authenticated;

  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<Uint8List>? requestStream,
      Future<void>? cancelFuture) async {
    if (options.path.endsWith('/me')) {
      if (!authenticated) return ResponseBody.fromString('', 401);
      return ResponseBody.fromString(jsonEncode({
        'id': 1, 'firstName': 'Alex', 'lastName': 'Example', 'email': 'alex@example.com',
      }), 200, headers: {Headers.contentTypeHeader: [Headers.jsonContentType]});
    }
    if (options.path.endsWith('/auth/csrf')) {
      return ResponseBody.fromString('', 204, headers: {'x-csrf-token': ['test-csrf-token']});
    }
    return ResponseBody.fromString('', 404);
  }

  @override
  void close({bool force = false}) {}
}
