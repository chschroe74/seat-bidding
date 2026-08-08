import 'package:dio/dio.dart';
import 'managed_cookie_store.dart';

ManagedCookieStore createManagedCookieStore() => _BrowserCookieStore();

class _BrowserCookieStore implements ManagedCookieStore {
  @override
  Future<void> attach(Dio dio) async {}

  @override
  Future<void> clear() async {}
}
