import 'web_push_client_stub.dart'
        if (dart.library.js_interop) 'web_push_client_web.dart'
        as platform;

abstract class WebPushClient {
    factory WebPushClient.platform() => platform.createWebPushClient();

    Future<WebPushCapability> capability();

    Future<LocalPushSubscription?> currentSubscription();

    Future<WebPushEnrollment> enroll(String applicationServerKey);

    Future<void> unsubscribe();
}

enum WebPushPermission { granted, denied, prompt, unsupported }

class WebPushCapability {
    const WebPushCapability({
        required this.supported,
        required this.canEnroll,
        required this.permission,
        required this.ios,
        required this.standalone,
        required this.deviceLabel,
    });

    final bool supported;
    final bool canEnroll;
    final WebPushPermission permission;
    final bool ios;
    final bool standalone;
    final String deviceLabel;
}

class LocalPushSubscription {
    const LocalPushSubscription({
        required this.endpoint,
        required this.p256dh,
        required this.auth,
        this.expirationTime,
    });

    final String endpoint;
    final String p256dh;
    final String auth;
    final DateTime? expirationTime;
}

class WebPushEnrollment {
    const WebPushEnrollment({required this.permission, this.subscription});

    final WebPushPermission permission;
    final LocalPushSubscription? subscription;
}

WebPushPermission webPushPermission(String? value) => switch (value) {
    'granted' => WebPushPermission.granted,
    'denied' => WebPushPermission.denied,
    'default' => WebPushPermission.prompt,
    _ => WebPushPermission.unsupported,
};