import 'package:dio/dio.dart';
import 'auth_service.dart';
import 'models.dart';

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
    Map<DateTime, int> values,
  ) async {
    final bids = values.entries
        .map(
          (entry) => {
            'date': entry.key.toIso8601String().substring(0, 10),
            'tokens': entry.value,
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
}
