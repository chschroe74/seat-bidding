import 'dart:convert';
import 'dart:js_interop';

import 'web_push_client.dart';

@JS('seatPush.capabilities')
external JSPromise<JSString> _capabilities();

@JS('seatPush.currentSubscription')
external JSPromise<JSString> _currentSubscription();

@JS('seatPush.enroll')
external JSPromise<JSString> _enroll(JSString applicationServerKey);

@JS('seatPush.unsubscribe')
external JSPromise<JSAny?> _unsubscribe();

WebPushClient createWebPushClient() => const BrowserWebPushClient();

class BrowserWebPushClient implements WebPushClient {
    const BrowserWebPushClient();

    @override
    Future<WebPushCapability> capability() async {
        final value = _decode(await _capabilities().toDart);
        return WebPushCapability(
            supported: value['supported'] as bool,
            canEnroll: value['canEnroll'] as bool,
            permission: webPushPermission(value['permission'] as String?),
            ios: value['ios'] as bool,
            standalone: value['standalone'] as bool,
            deviceLabel: value['deviceLabel'] as String,
        );
    }

    @override
    Future<LocalPushSubscription?> currentSubscription() async {
        final raw = jsonDecode((await _currentSubscription().toDart).toDart);
        return raw == null ? null : _subscription(raw as Map<String, dynamic>);
    }

    @override
    Future<WebPushEnrollment> enroll(String applicationServerKey) async {
        final value = _decode(await _enroll(applicationServerKey.toJS).toDart);
        final subscription = value['subscription'];
        return WebPushEnrollment(
            permission: webPushPermission(value['permission'] as String?),
            subscription: subscription == null
                    ? null
                    : _subscription(subscription as Map<String, dynamic>),
        );
    }

    @override
    Future<void> unsubscribe() async {
        await _unsubscribe().toDart;
    }

    static Map<String, dynamic> _decode(JSString value) =>
            jsonDecode(value.toDart) as Map<String, dynamic>;

    static LocalPushSubscription _subscription(Map<String, dynamic> value) {
        final keys = value['keys'] as Map<String, dynamic>;
        final expiration = value['expirationTime'];
        return LocalPushSubscription(
            endpoint: value['endpoint'] as String,
            p256dh: keys['p256dh'] as String,
            auth: keys['auth'] as String,
            expirationTime: expiration == null
                    ? null
                    : DateTime.fromMillisecondsSinceEpoch((expiration as num).round()),
        );
    }
}