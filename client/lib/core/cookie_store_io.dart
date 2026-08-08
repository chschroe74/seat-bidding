import 'dart:convert';
import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'managed_cookie_store.dart';

ManagedCookieStore createManagedCookieStore() => _SecureNativeCookieStore();

class _SecureNativeCookieStore implements ManagedCookieStore {
  final PersistCookieJar _jar = PersistCookieJar(
    persistSession: true,
    storage: _SecureStorage(),
  );

  @override
  Future<void> attach(Dio dio) async {
    await _jar.forceInit();
    dio.interceptors.add(CookieManager(_jar));
  }

  @override
  Future<void> clear() => _jar.deleteAll();
}

class _SecureStorage extends Storage {
  static const _prefix = 'seat_bidding_cookie_';
  final FlutterSecureStorage _storage = const FlutterSecureStorage();

  String _key(String key) => '$_prefix${base64Url.encode(utf8.encode(key))}';

  @override
  Future<void> init(bool persistSession, bool ignoreExpires) async {}

  @override
  Future<String?> read(String key) => _storage.read(key: _key(key));

  @override
  Future<void> write(String key, String value) => _storage.write(key: _key(key), value: value);

  @override
  Future<void> delete(String key) => _storage.delete(key: _key(key));

  @override
  Future<void> deleteAll(List<String> keys) async {
    final values = await _storage.readAll();
    for (final key in values.keys.where((key) => key.startsWith(_prefix))) {
      await _storage.delete(key: key);
    }
  }
}
