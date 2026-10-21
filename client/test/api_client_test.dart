import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:seat_bidding/core/api_client.dart';
import 'package:seat_bidding/core/auth_service.dart';
import 'package:seat_bidding/core/managed_cookie_store.dart';
import 'package:seat_bidding/core/models.dart';

void main() {
    test(
        'bid replacement refreshes CSRF and retries one rejected request',
        () async {
            final adapter = _BidAdapter();
            final dio = Dio()..httpClientAdapter = adapter;
            final auth = _TestAuthService(dio);
            final api = ApiClient(auth);

            final result = await api.replaceBids(
                7,
                {DateTime(2026, 8, 10): 12},
                {DateTime(2026, 8, 10): AttendancePeriod.morningOnly},
            );

            expect(result.bidTotal, 12);
            expect(result.seatCapacity, 6);
            expect(result.days.single.reservedSeatCount, 2);
            expect(result.days.single.assignableSeatCapacity, 4);
            expect(result.days.single.reservationDescription, 'Customer workshop');
            expect(adapter.putCount, 2);
            expect(adapter.attendancePeriod, 'MORNING_ONLY');
            expect(auth.refreshCount, 1);
        },
    );

    test(
        'admin rejection removes administrator state from the current UI',
        () async {
            final dio = Dio()..httpClientAdapter = _ForbiddenAdminAdapter();
            final auth = AuthService.testing(dio, _NoopCookieStore())
                ..user = const CurrentUser(
                    id: 1,
                    firstName: 'Ada',
                    lastName: 'Admin',
                    email: 'ada@example.com',
                    isAdmin: true,
                );
            final api = ApiClient(auth);

            await expectLater(
                api.seatReservations(DateTime(2030, 1, 1), DateTime(2030, 1, 31)),
                throwsA(isA<DioException>()),
            );
            expect(auth.user?.isAdmin, isFalse);
        },
    );

    test('floor-plan SVG URL is not prefixed with the API base path twice', () async {
        final adapter = _FloorPlanAdapter();
        final dio = Dio(BaseOptions(baseUrl: 'https://seat.test/api'))..httpClientAdapter = adapter;
        final api = ApiClient(AuthService.testing(dio, _NoopCookieStore()));

        final result = await api.floorPlanSvg('/api/floor-plan/versions/4/svg');

        expect(result, <int>[60, 115, 118, 103, 47, 62]);
        expect(adapter.requestPath, '/api/floor-plan/versions/4/svg');
        expect(adapter.acceptHeader, 'image/svg+xml');
    });

    test('missing current floor plan is an optional absence', () async {
        final dio = Dio(BaseOptions(baseUrl: 'https://seat.test/api'))
            ..httpClientAdapter = _MissingFloorPlanAdapter();
        final api = ApiClient(AuthService.testing(dio, _NoopCookieStore()));

        expect(await api.currentFloorPlan(), isNull);
    });

    test('guest preferences replace the complete sparse rating set', () async {
        final adapter = _GuestPreferencesAdapter();
        final dio = Dio(BaseOptions(baseUrl: 'https://seat.test/api'))..httpClientAdapter = adapter;
        final api = ApiClient(AuthService.testing(dio, _NoopCookieStore()));
        final current = GuestSeatPreferences(
            versionId: 7,
            preferenceRevision: 3,
            cutoffAt: DateTime.utc(2026, 10, 16),
            serverTime: DateTime.utc(2026, 10, 15),
            effectiveForCurrentRound: true,
            seats: const [],
        );

        final saved = await api.replaceGuestSeatPreferences(current, const [
            GuestSeatPreference(id: 10, number: 1, stars: 3),
            GuestSeatPreference(id: 11, number: 2, stars: 0),
        ]);

        expect(adapter.requestData, {
            'floorPlanVersionId': 7,
            'preferenceRevision': 3,
            'preferences': [
                {'seatId': 10, 'stars': 3},
            ],
        });
        expect(saved.preferenceRevision, 4);
    });
}

class _TestAuthService extends AuthService {
    _TestAuthService(Dio dio) : super.testing(dio, _NoopCookieStore());

    int refreshCount = 0;

    @override
    Future<void> refreshCsrf() async {
        refreshCount++;
    }
}

class _BidAdapter implements HttpClientAdapter {
    int putCount = 0;
    String? attendancePeriod;

    @override
    Future<ResponseBody> fetch(
        RequestOptions options,
        Stream<Uint8List>? requestStream,
        Future<void>? cancelFuture,
    ) async {
        if (options.method == 'PUT') {
            putCount++;
            attendancePeriod =
                    ((options.data as Map<String, dynamic>)['bids'] as List)
                                    .single['attendancePeriod']
                            as String;
            if (putCount == 1) {
                return ResponseBody.fromString(
                    jsonEncode({
                        'status': 400,
                        'code': 'REQUEST_REJECTED',
                        'detail': 'Request security or syntax validation failed.',
                    }),
                    400,
                    headers: {
                        Headers.contentTypeHeader: [Headers.jsonContentType],
                    },
                );
            }
            return ResponseBody.fromString(
                jsonEncode({
                    'roundId': 7,
                    'status': 'OPEN',
                    'cutoffAt': '2026-08-07T20:00:00Z',
                    'cutoffTimeZone': 'Europe/Berlin',
                    'serverTime': '2026-08-06T10:00:00Z',
                    'seatCapacity': 6,
                    'startingBalance': 60,
                    'bidTotal': 12,
                    'availableToBid': 48,
                    'days': [
                        {
                            'date': '2026-08-10',
                            'weekday': 'MONDAY',
                            'tokens': 12,
                            'reservedSeatCount': 2,
                            'assignableSeatCapacity': 4,
                            'reservationDescription': 'Customer workshop',
                        },
                    ],
                }),
                200,
                headers: {
                    Headers.contentTypeHeader: [Headers.jsonContentType],
                },
            );
        }
        return ResponseBody.fromString('', 404);
    }

    @override
    void close({bool force = false}) {}
}

class _ForbiddenAdminAdapter implements HttpClientAdapter {
    @override
    Future<ResponseBody> fetch(
        RequestOptions options,
        Stream<Uint8List>? requestStream,
        Future<void>? cancelFuture,
    ) async => ResponseBody.fromString(
        jsonEncode({
            'status': 403,
            'code': 'ADMIN_REQUIRED',
            'detail': 'Administrator access is required.',
        }),
        403,
        headers: {
            Headers.contentTypeHeader: [Headers.jsonContentType],
        },
    );
    @override
    void close({bool force = false}) {}
}

class _FloorPlanAdapter implements HttpClientAdapter {
    String? requestPath;
    String? acceptHeader;

    @override
    Future<ResponseBody> fetch(
        RequestOptions options,
        Stream<Uint8List>? requestStream,
        Future<void>? cancelFuture,
    ) async {
        requestPath = options.uri.path;
        acceptHeader = options.headers[Headers.acceptHeader] as String?;
        return ResponseBody.fromBytes(
            <int>[60, 115, 118, 103, 47, 62],
            200,
            headers: {Headers.contentTypeHeader: ['image/svg+xml']},
        );
    }

    @override
    void close({bool force = false}) {}
}

class _MissingFloorPlanAdapter implements HttpClientAdapter {
    @override
    Future<ResponseBody> fetch(
        RequestOptions options,
        Stream<Uint8List>? requestStream,
        Future<void>? cancelFuture,
    ) async => ResponseBody.fromString('', 404);

    @override
    void close({bool force = false}) {}
}

class _GuestPreferencesAdapter implements HttpClientAdapter {
    Object? requestData;

    @override
    Future<ResponseBody> fetch(
        RequestOptions options,
        Stream<Uint8List>? requestStream,
        Future<void>? cancelFuture,
    ) async {
        requestData = options.data;
        return ResponseBody.fromString(
            '{"floorPlanVersionId":7,"preferenceRevision":4,'
            '"cutoffAt":"2026-10-16T00:00:00Z","serverTime":"2026-10-15T00:00:00Z",'
            '"effectiveForCurrentRound":true,"seats":[]}',
            200,
            headers: {Headers.contentTypeHeader: ['application/json']},
        );
    }

    @override
    void close({bool force = false}) {}
}

class _NoopCookieStore implements ManagedCookieStore {
    @override
    Future<void> attach(Dio dio) async {}

    @override
    Future<void> clear() async {}
}