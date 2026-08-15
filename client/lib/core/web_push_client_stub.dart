import 'web_push_client.dart';

WebPushClient createWebPushClient() => const UnsupportedWebPushClient();

class UnsupportedWebPushClient implements WebPushClient {
    const UnsupportedWebPushClient();

    @override
    Future<WebPushCapability> capability() async => const WebPushCapability(
        supported: false,
        canEnroll: false,
        permission: WebPushPermission.unsupported,
        ios: false,
        standalone: false,
        deviceLabel: 'This device',
    );

    @override
    Future<LocalPushSubscription?> currentSubscription() async => null;

    @override
    Future<WebPushEnrollment> enroll(String applicationServerKey) async =>
            const WebPushEnrollment(permission: WebPushPermission.unsupported);

    @override
    Future<void> unsubscribe() async {}
}