import 'package:dio/dio.dart';

abstract class ManagedCookieStore {
  Future<void> attach(Dio dio);
  Future<void> clear();
}
