import 'package:dio/dio.dart';
import 'auth_service.dart';
import 'models.dart';
import 'web_push_client.dart';

class ApiClient {
    ApiClient(this.auth);
    final AuthService auth;

    Future<Assignments> latestAssignments() async => Assignments.fromJson(
        (await auth.dio.get<Map<String, dynamic>>('/assignments/latest')).data!,
    );
    Future<BiddingContext> currentBidding() async => BiddingContext.fromJson(
        (await auth.dio.get<Map<String, dynamic>>('/bidding/current')).data!,
    );
    Future<BiddingContext> replaceBids(
        int roundId,
        Map<DateTime, int> values, [
        Map<DateTime, AttendancePeriod>? attendancePeriods,
    ]) async {
        final bids = values.entries
                .map(
                    (entry) => {
                        'date': entry.key.toIso8601String().substring(0, 10),
                        'tokens': entry.value,
                        'attendancePeriod':
                                (attendancePeriods?[entry.key] ?? AttendancePeriod.fullDay)
                                        .wireValue,
                    },
                )
                .toList();
        Future<Response<Map<String, dynamic>>> send() =>
                auth.dio.put<Map<String, dynamic>>(
                    '/bidding/current/bids',
                    data: {'roundId': roundId, 'bids': bids},
                );
        try {
            return BiddingContext.fromJson((await send()).data!);
        } on DioException catch (error) {
            final problemCode = switch (error.error) {
                Problem problem => problem.code,
                _ => error.response?.data?['code'] as String?,
            };
            if (problemCode == 'CSRF_INVALID' || problemCode == 'REQUEST_REJECTED') {
                await auth.refreshCsrf();
                return BiddingContext.fromJson((await send()).data!);
            }
            rethrow;
        }
    }

    Future<SeatReservationList> seatReservations(
        DateTime from,
        DateTime to,
    ) async {
        try {
            return SeatReservationList.fromJson(
                (await auth.dio.get<Map<String, dynamic>>(
                    '/admin/seat-reservations',
                    queryParameters: {'from': _date(from), 'to': _date(to)},
                )).data!,
            );
        } on DioException catch (error) {
            if (error.response?.statusCode == 403 ||
                    error.error is Problem && (error.error as Problem).status == 403) {
                auth.revokeAdminAccess();
            }
            rethrow;
        }
    }

    Future<SeatReservation> createSeatReservation(
        DateTime date,
        int count,
        String? description,
    ) async {
        Future<Response<Map<String, dynamic>>> send() =>
                auth.dio.post<Map<String, dynamic>>(
                    '/admin/seat-reservations',
                    data: {
                        'date': _date(date),
                        'reservedSeatCount': count,
                        'description': description,
                    },
                );
        return SeatReservation.fromJson((await _adminMutation(send)).data!);
    }

    Future<void> deleteSeatReservation(int id) async {
        Future<Response<void>> send() =>
                auth.dio.delete<void>('/admin/seat-reservations/$id');
        await _adminMutation(send);
    }

    Future<NotificationSettings> notificationSettings() async =>
            NotificationSettings.fromJson(
                (await auth.dio.get<Map<String, dynamic>>(
                    '/settings/notifications',
                )).data!,
            );

    Future<NotificationSettings> updateNotificationSettings(
        bool enabled,
        ReminderWeekday weekday,
    ) async => NotificationSettings.fromJson(
        (await _mutation<Map<String, dynamic>>(
            () => auth.dio.put<Map<String, dynamic>>(
                '/settings/notifications',
                data: {
                    'bidRemindersEnabled': enabled,
                    'bidReminderStartWeekday': weekday.wireValue,
                },
            ),
        )).data!,
    );

    Future<RegisteredPushDevice> registerPushDevice(
        LocalPushSubscription subscription,
        String label,
    ) async => RegisteredPushDevice.fromJson(
        (await _mutation<Map<String, dynamic>>(
            () => auth.dio.post<Map<String, dynamic>>(
                '/settings/notifications/devices',
                data: {
                    'endpoint': subscription.endpoint,
                    'keys': {'p256dh': subscription.p256dh, 'auth': subscription.auth},
                    'expirationTime': subscription.expirationTime
                            ?.toUtc()
                            .toIso8601String(),
                    'deviceLabel': label,
                },
            ),
        )).data!,
    );

    Future<void> removePushDevice(int id) async {
        await _mutation<void>(
            () => auth.dio.delete<void>('/settings/notifications/devices/$id'),
        );
    }

    Future<void> suppressBidReminders(int roundId) async {
        await _mutation<void>(
            () => auth.dio.post<void>(
                '/settings/notifications/bid-reminders/current-round/suppression',
                data: {'roundId': roundId},
            ),
        );
    }

    Future<Response<T>> _mutation<T>(Future<Response<T>> Function() send) async {
        try {
            return await send();
        } on DioException catch (error) {
            final problem = error.error;
            if (problem is Problem &&
                    (problem.code == 'CSRF_INVALID' ||
                            problem.code == 'REQUEST_REJECTED')) {
                await auth.refreshCsrf();
                return send();
            }
            rethrow;
        }
    }

    Future<Response<T>> _adminMutation<T>(
        Future<Response<T>> Function() send,
    ) async {
        try {
            return await send();
        } on DioException catch (error) {
            final problem = error.error;
            if (problem is Problem &&
                    (problem.code == 'CSRF_INVALID' ||
                            problem.code == 'REQUEST_REJECTED')) {
                await auth.refreshCsrf();
                return send();
            }
            if (error.response?.statusCode == 403 ||
                    problem is Problem && problem.status == 403) {
                auth.revokeAdminAccess();
            }
            rethrow;
        }
    }

    static String _date(DateTime value) =>
            value.toIso8601String().substring(0, 10);
}