import 'package:flutter/material.dart';

class FloorPlanOverlayCard extends StatefulWidget {
    const FloorPlanOverlayCard({
        super.key,
        required this.mapSize,
        required this.title,
        required this.rowCount,
        required this.child,
        this.headerActions = const [],
        this.rowExtent = 52,
    });

    final Size mapSize;
    final String title;
    final int rowCount;
    final Widget child;
    final List<Widget> headerActions;
    final double rowExtent;

    @override
    State<FloorPlanOverlayCard> createState() => _FloorPlanOverlayCardState();
}

class _FloorPlanOverlayCardState extends State<FloorPlanOverlayCard> {
    bool collapsed = false;
    Offset offset = Offset.zero;

    @override
    Widget build(BuildContext context) {
        final width = widget.mapSize.width < 276 ? widget.mapSize.width - 16 : 260.0;
        final availableListHeight = widget.mapSize.height > 72 ? widget.mapSize.height - 72 : 0.0;
        final listHeight = (widget.rowCount * widget.rowExtent)
            .clamp(0.0, availableListHeight).toDouble();
        final height = collapsed ? 48.0 : 48.0 + listHeight;
        final maximumLeft = widget.mapSize.width - width - 8;
        final maximumTop = widget.mapSize.height - height - 8;
        final left = (widget.mapSize.width - width - 8 + offset.dx)
            .clamp(8.0, maximumLeft < 8 ? 8.0 : maximumLeft).toDouble();
        final top = (8 + offset.dy).clamp(8.0, maximumTop < 8 ? 8.0 : maximumTop).toDouble();
        return Positioned(
            left: left,
            top: top,
            width: width,
            child: Card(
                margin: EdgeInsets.zero,
                elevation: 8,
                clipBehavior: Clip.antiAlias,
                child: Column(mainAxisSize: MainAxisSize.min, children: [
                    GestureDetector(
                        behavior: HitTestBehavior.opaque,
                        onPanUpdate: (details) => setState(() => offset += details.delta),
                        child: MouseRegion(
                            cursor: SystemMouseCursors.move,
                            child: SizedBox(height: 48, child: Row(children: [
                                const SizedBox(width: 12),
                                Expanded(child: Text(widget.title,
                                    style: Theme.of(context).textTheme.titleSmall)),
                                ...widget.headerActions,
                                IconButton(
                                    onPressed: () => setState(() => collapsed = !collapsed),
                                    tooltip: collapsed ? 'Expand seat list' : 'Collapse seat list',
                                    icon: Icon(collapsed ? Icons.expand_more : Icons.expand_less),
                                ),
                            ])),
                        ),
                    ),
                    if (!collapsed) SizedBox(height: listHeight, child: widget.child),
                ]),
            ),
        );
    }
}