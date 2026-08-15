class PublicConfiguration {
    const PublicConfiguration({
        this.androidDownloadUrl,
        this.apiBasePath = '/api',
    });
    final String? androidDownloadUrl;
    final String apiBasePath;
    factory PublicConfiguration.fromJson(Map<String, dynamic> json) =>
            PublicConfiguration(
                androidDownloadUrl: json['androidDownloadUrl'] as String?,
                apiBasePath: json['apiBasePath'] as String? ?? '/api',
            );
}

class CurrentUser {
    const CurrentUser({
        required this.id,
        required this.firstName,
        required this.lastName,
        required this.email,
        this.isAdmin = false,
    });
    final int id;
    final String firstName;
    final String lastName;
    final String email;
    final bool isAdmin;
    CurrentUser copyWith({bool? isAdmin}) => CurrentUser(
        id: id,
        firstName: firstName,
        lastName: lastName,
        email: email,
        isAdmin: isAdmin ?? this.isAdmin,
    );
    factory CurrentUser.fromJson(Map<String, dynamic> json) => CurrentUser(
        id: json['id'] as int,
        firstName: json['firstName'] as String,
        lastName: json['lastName'] as String,
        email: json['email'] as String,
        isAdmin: json['isAdmin'] as bool? ?? false,
    );
}

enum AuthenticationNextStep { passwordRequired, codeRequired }

class AuthenticationStart {
    const AuthenticationStart({
        required this.nextStep,
        this.codeExpiresAt,
        this.resendAvailableAt,
    });
    final AuthenticationNextStep nextStep;
    final DateTime? codeExpiresAt;
    final DateTime? resendAvailableAt;
    factory AuthenticationStart.fromJson(Map<String, dynamic> json) =>
            AuthenticationStart(
                nextStep: json['nextStep'] == 'PASSWORD_REQUIRED'
                        ? AuthenticationNextStep.passwordRequired
                        : AuthenticationNextStep.codeRequired,
                codeExpiresAt: json['codeExpiresAt'] == null
                        ? null
                        : DateTime.parse(json['codeExpiresAt'] as String),
                resendAvailableAt: json['resendAvailableAt'] == null
                        ? null
                        : DateTime.parse(json['resendAvailableAt'] as String),
            );
}

class ActivationAuthorization {
    const ActivationAuthorization({required this.token, required this.expiresAt});
    final String token;
    final DateTime expiresAt;
    factory ActivationAuthorization.fromJson(Map<String, dynamic> json) =>
            ActivationAuthorization(
                token: json['activationToken'] as String,
                expiresAt: DateTime.parse(json['expiresAt'] as String),
            );
}

enum AttendancePeriod {
    fullDay('FULL_DAY', 'Full day'),
    morningOnly('MORNING_ONLY', 'Morning'),
    afternoonOnly('AFTERNOON_ONLY', 'Afternoon');

    const AttendancePeriod(this.wireValue, this.label);
    final String wireValue;
    final String label;
    AttendancePeriod get next => switch (this) {
        AttendancePeriod.fullDay => AttendancePeriod.morningOnly,
        AttendancePeriod.morningOnly => AttendancePeriod.afternoonOnly,
        AttendancePeriod.afternoonOnly => AttendancePeriod.fullDay,
    };
    static AttendancePeriod fromJson(Object? value) => switch (value) {
        'MORNING_ONLY' => AttendancePeriod.morningOnly,
        'AFTERNOON_ONLY' => AttendancePeriod.afternoonOnly,
        _ => AttendancePeriod.fullDay,
    };
}

enum AllocationUnitType { single, halfDayPair }

class BidDay {
    const BidDay({
        required this.date,
        required this.weekday,
        required this.tokens,
        this.attendancePeriod = AttendancePeriod.fullDay,
        this.reservedSeatCount = 0,
        int? assignableSeatCapacity,
        this.reservationDescription,
    }) : assignableSeatCapacity = assignableSeatCapacity ?? 0;
    final DateTime date;
    final String weekday;
    final int tokens;
    final AttendancePeriod attendancePeriod;
    final int reservedSeatCount;
    final int assignableSeatCapacity;
    final String? reservationDescription;
    factory BidDay.fromJson(Map<String, dynamic> json) => BidDay(
        date: DateTime.parse(json['date'] as String),
        weekday: json['weekday'] as String,
        tokens: json['tokens'] as int,
        attendancePeriod: AttendancePeriod.fromJson(json['attendancePeriod']),
        reservedSeatCount: json['reservedSeatCount'] as int? ?? 0,
        assignableSeatCapacity: json['assignableSeatCapacity'] as int? ?? 0,
        reservationDescription: json['reservationDescription'] as String?,
    );
}

class BiddingContext {
    const BiddingContext({
        required this.roundId,
        required this.status,
        required this.cutoffAt,
        required this.cutoffTimeZone,
        required this.serverTime,
        this.seatCapacity = 0,
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
    final int seatCapacity;
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
        seatCapacity: json['seatCapacity'] as int,
        startingBalance: json['startingBalance'] as int,
        bidTotal: json['bidTotal'] as int,
        availableToBid: json['availableToBid'] as int,
        days: (json['days'] as List)
                .map((value) => BidDay.fromJson(value as Map<String, dynamic>))
                .toList(),
    );
}

enum MyStatus { noBid, assigned, notAssigned }

class AssignmentParticipant {
    const AssignmentParticipant({
        required this.allocationUnitId,
        required this.unitType,
        required this.unitRank,
        required this.unitScoreTokens,
        required this.employeeId,
        required this.firstName,
        required this.lastName,
        required this.tokens,
        required this.attendancePeriod,
        required this.assigned,
        required this.displayRank,
        required this.isCurrentUser,
    });
    final int allocationUnitId;
    final AllocationUnitType unitType;
    final int unitRank;
    final int unitScoreTokens;
    final int employeeId;
    final String? firstName;
    final String? lastName;
    final int tokens;
    final AttendancePeriod attendancePeriod;
    final bool assigned;
    final int displayRank;
    final bool isCurrentUser;
    String get displayName => [
        firstName,
        lastName,
    ].whereType<String>().where((v) => v.isNotEmpty).join(' ');
    factory AssignmentParticipant.fromJson(Map<String, dynamic> json) =>
            AssignmentParticipant(
                allocationUnitId: json['allocationUnitId'] as int,
                unitType: json['unitType'] == 'HALF_DAY_PAIR'
                        ? AllocationUnitType.halfDayPair
                        : AllocationUnitType.single,
                unitRank: json['unitRank'] as int,
                unitScoreTokens: json['unitScoreTokens'] as int,
                employeeId: json['employeeId'] as int,
                firstName: json['firstName'] as String?,
                lastName: json['lastName'] as String?,
                tokens: json['tokens'] as int,
                attendancePeriod: AttendancePeriod.fromJson(json['attendancePeriod']),
                assigned: json['assigned'] as bool,
                displayRank: json['displayRank'] as int,
                isCurrentUser: json['isCurrentUser'] as bool,
            );
}

class AssignmentDay {
    const AssignmentDay({
        required this.date,
        required this.weekday,
        required this.myStatus,
        required this.occupiedSeatCount,
        required this.assignedEmployeeCount,
        required this.participants,
        this.reservedSeatCount = 0,
        int? assignableSeatCapacity,
        this.reservationDescription,
    }) : assignableSeatCapacity = assignableSeatCapacity ?? 0;
    final DateTime date;
    final String weekday;
    final MyStatus myStatus;
    final int occupiedSeatCount;
    final int assignedEmployeeCount;
    final int reservedSeatCount;
    final int assignableSeatCapacity;
    final String? reservationDescription;
    final List<AssignmentParticipant> participants;
    factory AssignmentDay.fromJson(Map<String, dynamic> json) => AssignmentDay(
        date: DateTime.parse(json['date'] as String),
        weekday: json['weekday'] as String,
        myStatus: switch (json['myStatus']) {
            'ASSIGNED' => MyStatus.assigned,
            'NOT_ASSIGNED' => MyStatus.notAssigned,
            _ => MyStatus.noBid,
        },
        occupiedSeatCount: json['occupiedSeatCount'] as int,
        assignedEmployeeCount: json['assignedEmployeeCount'] as int,
        reservedSeatCount: json['reservedSeatCount'] as int? ?? 0,
        assignableSeatCapacity: json['assignableSeatCapacity'] as int? ?? 0,
        reservationDescription: json['reservationDescription'] as String?,
        participants: (json['participants'] as List)
                .map(
                    (value) =>
                            AssignmentParticipant.fromJson(value as Map<String, dynamic>),
                )
                .toList(),
    );
}

class SeatReservation {
    const SeatReservation({
        required this.id,
        required this.date,
        required this.reservedSeatCount,
        required this.physicalSeatCapacity,
        required this.mutable,
        this.description,
        this.cutoffAt,
        this.roundStatus,
    });
    final int id;
    final DateTime date;
    final int reservedSeatCount;
    final int physicalSeatCapacity;
    final String? description;
    final bool mutable;
    final DateTime? cutoffAt;
    final String? roundStatus;
    factory SeatReservation.fromJson(Map<String, dynamic> json) =>
            SeatReservation(
                id: json['id'] as int,
                date: DateTime.parse(json['date'] as String),
                reservedSeatCount: json['reservedSeatCount'] as int,
                physicalSeatCapacity: json['physicalSeatCapacity'] as int,
                description: json['description'] as String?,
                mutable: json['mutable'] as bool,
                cutoffAt: json['cutoffAt'] == null
                        ? null
                        : DateTime.parse(json['cutoffAt'] as String),
                roundStatus: json['roundStatus'] as String?,
            );
}

class SeatReservationList {
    const SeatReservationList({
        required this.serverTime,
        required this.timeZone,
        required this.reservations,
    });
    final DateTime serverTime;
    final String timeZone;
    final List<SeatReservation> reservations;
    factory SeatReservationList.fromJson(Map<String, dynamic> json) =>
            SeatReservationList(
                serverTime: DateTime.parse(json['serverTime'] as String),
                timeZone: json['timeZone'] as String,
                reservations: (json['reservations'] as List)
                        .map(
                            (value) =>
                                    SeatReservation.fromJson(value as Map<String, dynamic>),
                        )
                        .toList(),
            );
}

class Assignments {
    const Assignments({
        required this.roundId,
        required this.publishedAt,
        required this.seatCapacity,
        required this.days,
    });
    final int roundId;
    final DateTime publishedAt;
    final int seatCapacity;
    final List<AssignmentDay> days;
    factory Assignments.fromJson(Map<String, dynamic> json) => Assignments(
        roundId: json['roundId'] as int,
        publishedAt: DateTime.parse(json['publishedAt'] as String),
        seatCapacity: json['seatCapacity'] as int,
        days: (json['days'] as List)
                .map((value) => AssignmentDay.fromJson(value as Map<String, dynamic>))
                .toList(),
    );
}

class Problem implements Exception {
    const Problem({
        required this.status,
        required this.code,
        required this.detail,
    });
    final int status;
    final String code;
    final String detail;
    @override
    String toString() => detail;
}