{{flutter_js}}
{{flutter_build_config}}

(async () => {
    if ('serviceWorker' in navigator) {
        const hadController = navigator.serviceWorker.controller !== null;
        let reloading = false;
        if (hadController) {
            navigator.serviceWorker.addEventListener('controllerchange', () => {
                if (reloading) return;
                reloading = true;
                window.location.reload();
            });
        }
        try {
            const registration = await navigator.serviceWorker.register(
                '/web_push_service_worker.js',
                { scope: '/', updateViaCache: 'none' },
            );
            await registration.update();
        }
        catch (error) {
            console.warn('The Web Push service worker could not be updated.', error);
        }
    }
    await _flutter.loader.load();
})();