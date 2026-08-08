import 'package:dio/dio.dart';
import '../../core/models.dart';

String authenticationError(Object error) {
  if (error is DioException && error.error is Problem) return (error.error as Problem).detail;
  return 'The request could not be completed. Please try again.';
}
