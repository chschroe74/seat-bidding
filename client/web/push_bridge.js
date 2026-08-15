(function () {
    const isStandalone = () => window.matchMedia('(display-mode: standalone)').matches
        || window.navigator.standalone === true;
    const isIos = () => /iPad|iPhone|iPod/.test(navigator.userAgent)
        || navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1;
    const supported = () => 'serviceWorker' in navigator && 'PushManager' in window
        && 'Notification' in window;
    const encode = (value) => JSON.stringify(value);
    const subscriptionValue = (subscription) => subscription ? subscription.toJSON() : null;
    const decodeKey = (value) => {
        const padding = '='.repeat((4 - value.length % 4) % 4);
        const raw = atob((value + padding).replace(/-/g, '+').replace(/_/g, '/'));
        return Uint8Array.from(raw, (character) => character.charCodeAt(0));
    };
    const label = () => {
        if (isIos()) return isStandalone() ? 'iPhone/iPad · Home Screen web app' : 'iPhone/iPad · Browser';
        if (/Android/i.test(navigator.userAgent)) return 'Android · Web browser';
        return 'Desktop · Web browser';
    };

    window.seatPush = {
        capabilities: async () => encode({
            supported: supported(),
            canEnroll: supported() && (!isIos() || isStandalone()),
            permission: supported() ? Notification.permission : 'unsupported',
            ios: isIos(),
            standalone: isStandalone(),
            deviceLabel: label(),
        }),
        currentSubscription: async () => {
            if (!supported()) return encode(null);
            const registration = await navigator.serviceWorker.ready;
            return encode(subscriptionValue(await registration.pushManager.getSubscription()));
        },
        enroll: async (applicationServerKey) => {
            if (!supported() || isIos() && !isStandalone()) {
                return encode({ permission: 'unsupported', subscription: null });
            }
            const permission = Notification.permission === 'default'
                ? await Notification.requestPermission()
                : Notification.permission;
            if (permission !== 'granted') return encode({ permission, subscription: null });
            const registration = await navigator.serviceWorker.ready;
            let subscription = await registration.pushManager.getSubscription();
            if (!subscription) {
                subscription = await registration.pushManager.subscribe({
                    userVisibleOnly: true,
                    applicationServerKey: decodeKey(applicationServerKey),
                });
            }
            return encode({ permission, subscription: subscriptionValue(subscription) });
        },
        unsubscribe: async () => {
            if (!supported()) return;
            const registration = await navigator.serviceWorker.ready;
            const subscription = await registration.pushManager.getSubscription();
            if (subscription) await subscription.unsubscribe();
        },
    };
})();