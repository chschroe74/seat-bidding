import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';
import 'cookie_store.dart';
import 'http_adapter.dart';
import 'managed_cookie_store.dart';
import 'models.dart';

class AuthService extends ChangeNotifier {
  AuthService._(this.configuration, this.dio, this._cookies, this._nativeApiBase);

  @visibleForTesting
  AuthService.testing(this.dio, this._cookies)
      : configuration = const PublicConfiguration(),
        _nativeApiBase = null;

  final PublicConfiguration configuration;
  final Dio dio;
  final ManagedCookieStore _cookies;
  final Uri? _nativeApiBase;
  CurrentUser? user;
  String? email;
  String? activationToken;
  DateTime? activationTokenExpiresAt;
  DateTime? codeExpiresAt;
  DateTime? resendAvailableAt;
  String? csrfToken;
  bool initialized = false;
  String _intendedRoute = '/assignments';

  bool get isAuthenticated => user != null;

  static Future<AuthService> initialize() async {
    const configuredBase = String.fromEnvironment('API_BASE_URL');
    Uri? nativeBase;
    if (!kIsWeb) {
      nativeBase = Uri.tryParse(configuredBase);
      if (nativeBase == null || nativeBase.scheme != 'https' || !nativeBase.hasAuthority ||
          !nativeBase.path.endsWith('/api')) {
        throw StateError('Android requires an HTTPS API_BASE_URL ending in /api.');
      }
    }
    final bootstrap = Dio(BaseOptions(baseUrl: kIsWeb ? '' : configuredBase));
    configureBrowserCredentials(bootstrap);
    final response = await bootstrap.get<Map<String, dynamic>>(
      kIsWeb ? '/api/public/configuration' : '/public/configuration',
    );
    final config = PublicConfiguration.fromJson(response.data!);
    final dio = Dio(BaseOptions(
      baseUrl: kIsWeb ? config.apiBasePath : configuredBase,
      headers: const {'Accept': 'application/json'},
    ));
    configureBrowserCredentials(dio);
    final cookies = createManagedCookieStore();
    await cookies.attach(dio);
    final service = AuthService._(config, dio, cookies, nativeBase);
    dio.interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) {
        if (service.csrfToken != null && _changesState(options.method) &&
            !options.uri.path.endsWith('/j_security_check')) {
          options.headers['X-CSRF-TOKEN'] = service.csrfToken;
        }
        handler.next(options);
      },
      onError: (error, handler) async {
        final isLogin = error.requestOptions.uri.path.endsWith('/j_security_check');
        if (error.response?.statusCode == 401 && !isLogin) {
          await service._clearSession(clearCookies: true, notify: true);
        }
        final data = error.response?.data;
        if (data is Map<String, dynamic>) {
          handler.reject(DioException(
            requestOptions: error.requestOptions,
            response: error.response,
            error: Problem(
              status: data['status'] as int? ?? 500,
              code: data['code'] as String? ?? 'ERROR',
              detail: data['detail'] as String? ?? 'The request failed.',
            ),
          ));
        } else {
          handler.next(error);
        }
      },
    ));
    await service.refreshCsrf();
    await service.restore();
    service.initialized = true;
    return service;
  }

  Future<void> restore() async {
    try {
      user = CurrentUser.fromJson((await dio.get<Map<String, dynamic>>('/me')).data!);
    } on DioException catch (error) {
      if (error.response?.statusCode != 401) rethrow;
      await _clearSession(clearCookies: true);
      await refreshCsrf();
    }
  }

  Future<AuthenticationStart> start(String submittedEmail) async {
    await _ensureCsrf();
    email = submittedEmail.trim();
    final response = await dio.post<Map<String, dynamic>>('/auth/start', data: {'email': email});
    final result = AuthenticationStart.fromJson(response.data!);
    codeExpiresAt = result.codeExpiresAt;
    resendAvailableAt = result.resendAvailableAt;
    notifyListeners();
    return result;
  }

  Future<void> login(String password) async {
    final currentEmail = email;
    if (currentEmail == null) throw StateError('The email-first login flow has not been started.');
    final root = kIsWeb ? Uri.base.origin : _nativeApiBase!.origin;
    final headers = <String, dynamic>{};
    if (!kIsWeb) headers['Origin'] = root;
    await dio.postUri<void>(
      Uri.parse('$root/j_security_check'),
      data: {'j_username': currentEmail, 'j_password': password},
      options: Options(contentType: Headers.formUrlEncodedContentType, headers: headers),
    );
    user = CurrentUser.fromJson((await dio.get<Map<String, dynamic>>('/me')).data!);
    notifyListeners();
  }

  Future<void> resend() async {
    await _ensureCsrf();
    try {
      final response = await dio.post<void>('/auth/activation/resend', data: {'email': email});
      _applyRetryAfter(response.headers.value('retry-after'));
    } on DioException catch (error) {
      if (error.response?.statusCode == 429) _applyRetryAfter(error.response?.headers.value('retry-after'));
      rethrow;
    }
  }

  Future<ActivationAuthorization> verifyCode(String code) async {
    await _ensureCsrf();
    final response = await dio.post<Map<String, dynamic>>('/auth/activation/verify',
      data: {'email': email, 'code': code});
    final authorization = ActivationAuthorization.fromJson(response.data!);
    activationToken = authorization.token;
    activationTokenExpiresAt = authorization.expiresAt;
    notifyListeners();
    return authorization;
  }

  Future<void> createPassword(String password, String confirmation) async {
    await _ensureCsrf();
    await dio.post<void>('/auth/activation/password', data: {
      'activationToken': activationToken,
      'password': password,
      'passwordConfirmation': confirmation,
    });
    activationToken = null;
    activationTokenExpiresAt = null;
    await login(password);
  }

  Future<void> refreshCsrf() async {
    final response = await dio.get<void>('/auth/csrf');
    csrfToken = response.headers.value('x-csrf-token');
    if (csrfToken == null || csrfToken!.isEmpty) {
      throw StateError('The server did not provide a CSRF token.');
    }
  }

  Future<void> _ensureCsrf() async {
    if (csrfToken == null) await refreshCsrf();
  }

  Future<void> logout() async {
    await _ensureCsrf();
    try {
      await dio.post<void>('/auth/logout');
    } on DioException catch (error) {
      if (error.response?.statusCode != 401) rethrow;
    }
    await _clearSession(clearCookies: true, notify: true);
    await refreshCsrf();
  }

  Future<void> _clearSession({required bool clearCookies, bool notify = false}) async {
    user = null;
    csrfToken = null;
    activationToken = null;
    activationTokenExpiresAt = null;
    codeExpiresAt = null;
    resendAvailableAt = null;
    email = null;
    if (clearCookies) await _cookies.clear();
    if (notify) notifyListeners();
  }

  void rememberIntendedRoute(String route) {
    if (route == '/assignments' || route == '/bids' || route == '/help') _intendedRoute = route;
  }

  String takeIntendedRoute() {
    final route = _intendedRoute;
    _intendedRoute = '/assignments';
    return route;
  }

  void _applyRetryAfter(String? value) {
    final seconds = int.tryParse(value ?? '');
    if (seconds != null && seconds > 0) {
      resendAvailableAt = DateTime.now().toUtc().add(Duration(seconds: seconds));
      notifyListeners();
    }
  }

  static bool _changesState(String method) =>
      const {'POST', 'PUT', 'PATCH', 'DELETE'}.contains(method.toUpperCase());
}
