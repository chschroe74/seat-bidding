String? validateNewPassword(String password, String confirmation) {
  if (password != confirmation) return 'Password and confirmation must match exactly.';
  if (password.runes.length < 15 || password.runes.length > 128) {
    return 'Use 15 to 128 characters. Spaces and Unicode characters are allowed.';
  }
  return null;
}
