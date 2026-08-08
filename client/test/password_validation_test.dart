import 'package:flutter_test/flutter_test.dart';
import 'package:seat_bidding/features/auth/password_validation.dart';

void main() {
  test('new password validation covers every policy boundary', () {
    expect(validateNewPassword('Valid-password-123', 'different-password-123'), contains('match'));
    expect(validateNewPassword('fourteen chars', 'fourteen chars'), isNotNull);
    expect(validateNewPassword('lowercase words with spaces', 'lowercase words with spaces'), isNull);
    expect(validateNewPassword('Unicode seat 🚺 password', 'Unicode seat 🚺 password'), isNull);
    final maximum = 'x' * 128;
    final tooLong = 'x' * 129;
    expect(validateNewPassword(maximum, maximum), isNull);
    expect(validateNewPassword(tooLong, tooLong), isNotNull);
    expect(validateNewPassword('Valid password 123!', 'Valid password 123!'), isNull);
  });
}
