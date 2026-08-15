{{flutter_js}}
{{flutter_build_config}}

(async () => {
    if ('serviceWorker' in navigator) {
        await navigator.serviceWorker.register('/web_push_service_worker.js', { scope: '/' });
    }
    await _flutter.loader.load();
})();