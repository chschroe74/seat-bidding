class PublicConfiguration {
  const PublicConfiguration({this.androidDownloadUrl, this.apiBasePath = '/api'});
  final String? androidDownloadUrl;
  final String apiBasePath;
  factory PublicConfiguration.fromJson(Map<String, dynamic> json) => PublicConfiguration(
        androidDownloadUrl: json['androidDownloadUrl'] as String?,
        apiBasePath: json['apiBasePath'] as String? ?? '/api',
      );
}

class CurrentUser {
  const CurrentUser({required this.id, required this.firstName, required this.lastName, required this.email});
  final int id;
  final String firstName;
  final String lastName;
  final String email;
  factory CurrentUser.fromJson(Map<String, dynamic> json) => CurrentUser(
    id: json['id'] as int, firstName: json['firstName'] as String,
    lastName: json['lastName'] as String, email: json['email'] as String,
  );
}

enum AuthenticationNextStep { passwordRequired, codeRequired }

class AuthenticationStart {
  const AuthenticationStart({required this.nextStep, this.codeExpiresAt, this.resendAvailableAt});
  final AuthenticationNextStep nextStep;
  final DateTime? codeExpiresAt;
  final DateTime? resendAvailableAt;
  factory AuthenticationStart.fromJson(Map<String, dynamic> json) => AuthenticationStart(
    nextStep: json['nextStep'] == 'PASSWORD_REQUIRED'
        ? AuthenticationNextStep.passwordRequired : AuthenticationNextStep.codeRequired,
    codeExpiresAt: json['codeExpiresAt'] == null ? null : DateTime.parse(json['codeExpiresAt'] as String),
    resendAvailableAt: json['resendAvailableAt'] == null ? null : DateTime.parse(json['resendAvailableAt'] as String),
  );
}

class ActivationAuthorization {
  const ActivationAuthorization({required this.token, required this.expiresAt});
  final String token;
  final DateTime expiresAt;
  factory ActivationAuthorization.fromJson(Map<String, dynamic> json) => ActivationAuthorization(
    token: json['activationToken'] as String, expiresAt: DateTime.parse(json['expiresAt'] as String),
  );
}

class BidDay {
  const BidDay({required this.date, required this.weekday, required this.tokens});
  final DateTime date;
  final String weekday;
  final int tokens;
  factory BidDay.fromJson(Map<String, dynamic> json) => BidDay(
        date: DateTime.parse(json['date'] as String),
        weekday: json['weekday'] as String,
        tokens: json['tokens'] as int,
      );
}

class BiddingContext {
  const BiddingContext({
    required this.roundId,
    required this.status,
    required this.cutoffAt,
    required this.cutoffTimeZone,
    required this.serverTime,
    required this.startingBalance,
    required this.bidTotal,
    required this.availableToBid,
    required this.days,
  });
  final int roundId;
  final String status;
  final DateTime cutoffAt;
  final String cutoffTimeZone;
  final DateTime serverTime;
  final int startingBalance;
  final int bidTotal;
  final int availableToBid;
  final List<BidDay> days;
  factory BiddingContext.fromJson(Map<String, dynamic> json) => BiddingContext(
        roundId: json['roundId'] as int,
        status: json['status'] as String,
        cutoffAt: DateTime.parse(json['cutoffAt'] as String),
        cutoffTimeZone: json['cutoffTimeZone'] as String,
        serverTime: DateTime.parse(json['serverTime'] as String),
        startingBalance: json['startingBalance'] as int,
        bidTotal: json['bidTotal'] as int,
        availableToBid: json['availableToBid'] as int,
        days: (json['days'] as List).map((value) => BidDay.fromJson(value as Map<String, dynamic>)).toList(),
      );
}

enum MyStatus { noBid, assigned, notAssigned }

class AssignmentParticipant {
  const AssignmentParticipant({
    required this.employeeId,
    required this.firstName,
    required this.lastName,
    required this.tokens,
    required this.assigned,
    required this.rank,
    required this.isCurrentUser,
  });
  final int employeeId;
  final String? firstName;
  final String? lastName;
  final int tokens;
  final bool assigned;
  final int rank;
  final bool isCurrentUser;
  String get displayName => [firstName, lastName].whereType<String>().where((v) => v.isNotEmpty).join(' ');
  factory AssignmentParticipant.fromJson(Map<String, dynamic> json) => AssignmentParticipant(
        employeeId: json['employeeId'] as int,
        firstName: json['firstName'] as String?,
        lastName: json['lastName'] as String?,
        tokens: json['tokens'] as int,
        assigned: json['assigned'] as bool,
        rank: json['rank'] as int,
        isCurrentUser: json['isCurrentUser'] as bool,
      );
}

class AssignmentDay {
  const AssignmentDay({required this.date, required this.weekday, required this.myStatus,
    required this.assignedCount, required this.participants});
  final DateTime date;
  final String weekday;
  final MyStatus myStatus;
  final int assignedCount;
  final List<AssignmentParticipant> participants;
  factory AssignmentDay.fromJson(Map<String, dynamic> json) => AssignmentDay(
        date: DateTime.parse(json['date'] as String),
        weekday: json['weekday'] as String,
        myStatus: switch (json['myStatus']) {
          'ASSIGNED' => MyStatus.assigned,
          'NOT_ASSIGNED' => MyStatus.notAssigned,
          _ => MyStatus.noBid,
        },
        assignedCount: json['assignedCount'] as int,
        participants: (json['participants'] as List)
            .map((value) => AssignmentParticipant.fromJson(value as Map<String, dynamic>)).toList(),
      );
}

class Assignments {
  const Assignments({required this.roundId, required this.publishedAt, required this.seatCapacity, required this.days});
  final int roundId;
  final DateTime publishedAt;
  final int seatCapacity;
  final List<AssignmentDay> days;
  factory Assignments.fromJson(Map<String, dynamic> json) => Assignments(
        roundId: json['roundId'] as int,
        publishedAt: DateTime.parse(json['publishedAt'] as String),
        seatCapacity: json['seatCapacity'] as int,
        days: (json['days'] as List).map((value) => AssignmentDay.fromJson(value as Map<String, dynamic>)).toList(),
      );
}

class Problem implements Exception {
  const Problem({required this.status, required this.code, required this.detail});
  final int status;
  final String code;
  final String detail;
  @override String toString() => detail;
}
