import 'dart:io';
import 'dart:ui' as ui;

import 'package:flutter_test/flutter_test.dart';

void main() {
    test('web icons cover manifest, favicon, and Apple discovery paths', () async {
        await _expectPng('web/icons/seat-192.png', 192);
        await _expectPng('web/icons/seat-512.png', 512);
        await _expectPng('web/apple-touch-icon.png', 180);
        await _expectPng('web/apple-touch-icon-precomposed.png', 180);
        await _expectPng('web/apple-touch-icon-120x120.png', 120);
        await _expectPng('web/apple-touch-icon-120x120-precomposed.png', 120);

        final favicon = File('web/favicon.ico').readAsBytesSync();
        expect(favicon.take(4), [0, 0, 1, 0]);

        final index = File('web/index.html').readAsStringSync();
        expect(index, contains('href="favicon.ico"'));
        expect(index, contains('href="apple-touch-icon.png" sizes="180x180"'));
        expect(index, contains('href="apple-touch-icon-120x120.png" sizes="120x120"'));

        final manifest = File('web/manifest.json').readAsStringSync();
        expect(manifest, contains('"icons/seat-192.png"'));
        expect(manifest, contains('"icons/seat-512.png"'));
    });
}

Future<void> _expectPng(String path, int expectedSize) async {
    final bytes = File(path).readAsBytesSync();
    expect(bytes.take(8), [137, 80, 78, 71, 13, 10, 26, 10]);
    final codec = await ui.instantiateImageCodec(bytes);
    final frame = await codec.getNextFrame();
    expect(frame.image.width, expectedSize);
    expect(frame.image.height, expectedSize);
    frame.image.dispose();
    codec.dispose();
}