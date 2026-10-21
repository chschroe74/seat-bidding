import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_svg/flutter_svg.dart';

class FloorPlanSvgImage extends StatefulWidget {
    const FloorPlanSvgImage({super.key, required this.bytes, required this.semanticsLabel});

    final Uint8List bytes;
    final String semanticsLabel;

    @override
    State<FloorPlanSvgImage> createState() => _FloorPlanSvgImageState();
}

class _FloorPlanSvgImageState extends State<FloorPlanSvgImage> {
    String? browserDataUrl;

    @override
    void initState() {
        super.initState();
        _encodeForBrowser();
    }

    @override
    void didUpdateWidget(FloorPlanSvgImage oldWidget) {
        super.didUpdateWidget(oldWidget);
        if (!identical(widget.bytes, oldWidget.bytes)) _encodeForBrowser();
    }

    void _encodeForBrowser() {
        browserDataUrl = kIsWeb ? 'data:image/svg+xml;base64,${base64Encode(widget.bytes)}' : null;
    }

    @override
    Widget build(BuildContext context) {
        if (kIsWeb) {
            return Image.network(
                browserDataUrl!,
                fit: BoxFit.fill,
                semanticLabel: widget.semanticsLabel,
                webHtmlElementStrategy: WebHtmlElementStrategy.prefer,
            );
        }
        return SvgPicture.memory(
            widget.bytes,
            fit: BoxFit.fill,
            semanticsLabel: widget.semanticsLabel,
        );
    }
}