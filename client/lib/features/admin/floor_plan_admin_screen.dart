import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:file_selector/file_selector.dart';

import '../../core/api_client.dart';
import '../../core/models.dart';

class FloorPlanAdminScreen extends StatefulWidget {
    const FloorPlanAdminScreen({super.key, required this.api});
    final ApiClient api;
    @override
    State<FloorPlanAdminScreen> createState() => _FloorPlanAdminScreenState();
}

class _FloorPlanAdminScreenState extends State<FloorPlanAdminScreen> {
    AdminFloorPlan? state;
    Uint8List? svg;
    int? selectedSeat;
    bool addMode = false;
    bool busy = true;
    String? error;

    @override
    void initState() { super.initState(); load(); }

    Future<void> load() async {
        try {
            final loaded = await widget.api.adminFloorPlan();
            await update(loaded);
        } on DioException catch (failure) {
            if (mounted) setState(() { busy = false; error = _detail(failure); });
        }
    }

    Future<void> update(AdminFloorPlan value) async {
        final version = value.draft ?? value.activeVersion;
        final bytes = version == null ? null : await widget.api.floorPlanSvg(version.svgUrl);
        if (mounted) {
            setState(() {
                state = value; svg = bytes; busy = false; error = null; selectedSeat = null; addMode = false;
            });
        }
    }

    Future<void> upload() async {
        const group = XTypeGroup(label: 'SVG floor plans', extensions: ['svg'], mimeTypes: ['image/svg+xml']);
        final file = await openFile(acceptedTypeGroups: const [group]);
        if (file == null) return;
        await mutate(() async => widget.api.uploadFloorPlan(await file.readAsBytes()));
    }

    Future<void> mapTap(TapUpDetails details, Size size) async {
        final draft = state?.draft;
        if (draft == null) return;
        final x = (details.localPosition.dx / size.width).clamp(0.0, 1.0).toDouble();
        final y = (details.localPosition.dy / size.height).clamp(0.0, 1.0).toDouble();
        if (addMode) {
            await mutate(() => widget.api.addFloorPlanSeat(draft.draftRevision, x, y));
        } else if (selectedSeat != null) {
            await mutate(() => widget.api.moveFloorPlanSeat(selectedSeat!, draft.draftRevision, x, y));
        }
    }

    Future<void> mutate(Future<AdminFloorPlan> Function() action) async {
        setState(() { busy = true; error = null; });
        try { await update(await action()); }
        on DioException catch (failure) { if (mounted) setState(() { busy = false; error = _detail(failure); }); }
    }

    @override
    Widget build(BuildContext context) {
        final draft = state?.draft;
        final shown = draft ?? state?.activeVersion;
        return ListView(padding: const EdgeInsets.all(24), children: [
            Text('Floor plan', style: Theme.of(context).textTheme.headlineMedium),
            Text(state?.pendingDeactivation == true ? 'Deactivation is pending for the next round.'
                : state?.pendingVersion != null ? 'A published version is pending for the next round.'
                : state?.activeVersion != null ? 'Floor-plan mode is active.' : 'Floor-plan mode is disabled.'),
            const SizedBox(height: 12),
            Wrap(spacing: 8, runSpacing: 8, children: [
                FilledButton.icon(onPressed: busy ? null : upload, icon: const Icon(Icons.upload_file),
                    label: Text(draft == null ? 'Upload SVG' : 'Replace SVG')),
                if (draft != null) FilledButton(onPressed: busy || draft.seats.where((s) => s.state == 'ACTIVE').isEmpty
                    ? null : () => mutate(() => widget.api.publishFloorPlan(draft.draftRevision)),
                    child: const Text('Publish floor plan')),
                if (draft != null) OutlinedButton.icon(
                    onPressed: busy ? null : () => setState(() { addMode = !addMode; selectedSeat = null; }),
                    icon: const Icon(Icons.add_location_alt_outlined),
                    label: Text(addMode ? 'Cancel adding' : 'Add seat on map')),
                if (draft != null) OutlinedButton.icon(
                    onPressed: busy ? null : () => _addByCoordinates(context, draft),
                    icon: const Icon(Icons.pin_drop_outlined), label: const Text('Add by coordinates')),
                if (draft != null) OutlinedButton(onPressed: busy ? null : () async {
                    setState(() => busy = true); await widget.api.discardFloorPlanDraft(); await load();
                }, child: const Text('Discard draft')),
                if (draft != null && draft.seats.isNotEmpty)
                    OutlinedButton(onPressed: busy ? null : () => _confirmRemoveAll(context, draft),
                        child: const Text('Remove all existing seats')),
                if (state?.activeVersion != null && draft == null && state?.pendingVersion == null)
                    OutlinedButton(onPressed: busy ? null : () => _confirmDeactivate(context),
                        child: const Text('Delete floor plan')),
            ]),
            const SizedBox(height: 12),
            for (final warning in state?.warnings ?? const <String>[])
                Card(color: Theme.of(context).colorScheme.errorContainer,
                    child: ListTile(leading: const Icon(Icons.warning_amber), title: Text(warning))),
            if (draft != null) Text(addMode ? 'Add-seat mode: click the map to place the next numbered seat.'
                : selectedSeat == null ? 'Pan or zoom the map, or select a marker to move it.'
                : 'Seat selected. Click the map to move it.'),
            if (shown != null && svg != null)
                AspectRatio(aspectRatio: shown.viewBox.width / shown.viewBox.height,
                    child: InteractiveViewer(minScale: 0.5, maxScale: 6,
                        boundaryMargin: const EdgeInsets.all(80),
                        child: LayoutBuilder(builder: (context, constraints) => GestureDetector(
                            onTapUp: busy || draft == null || (!addMode && selectedSeat == null)
                                ? null : (details) => mapTap(details, constraints.biggest),
                            child: Stack(children: [
                                Positioned.fill(child: Image.memory(svg!, fit: BoxFit.fill,
                                    semanticLabel: 'Sanitized office floor plan')),
                                for (final seat in shown.seats) Positioned(
                                    left: seat.x * constraints.maxWidth - 20,
                                    top: seat.y * constraints.maxHeight - 20,
                                    child: Semantics(label: 'Seat ${seat.number}, ${seat.state}', button: true,
                                        child: IconButton.filled(
                                            style: IconButton.styleFrom(backgroundColor: seat.state == 'PENDING_REMOVAL'
                                                ? Theme.of(context).colorScheme.errorContainer : null),
                                            onPressed: draft == null ? null : () => setState(() {
                                                selectedSeat = seat.id; addMode = false;
                                            }),
                                            tooltip: 'Seat ${seat.number}', icon: Text('${seat.number}'),
                                        )),
                                ),
                            ]),
                        )),
                    ),
                ),
            if (draft != null)
                for (final seat in draft.seats) ListTile(
                    title: Text('Seat ${seat.number}'),
                    subtitle: Text(seat.inherited ? '${seat.state} · permanent number' : 'New in this draft'),
                    selected: selectedSeat == seat.id,
                    onTap: () => setState(() => selectedSeat = seat.id),
                    trailing: Wrap(children: [
                        IconButton(icon: const Icon(Icons.edit_location_alt_outlined), tooltip: 'Move by coordinates',
                            onPressed: busy || seat.state != 'ACTIVE' ? null
                                : () => _moveByCoordinates(context, draft, seat)),
                        IconButton(
                            icon: Icon(seat.state == 'PENDING_REMOVAL' ? Icons.restore : Icons.delete_outline),
                            tooltip: seat.state == 'PENDING_REMOVAL' ? 'Restore seat' : 'Remove seat',
                            onPressed: busy ? null : () => mutate(() => seat.state == 'PENDING_REMOVAL'
                                ? widget.api.restoreFloorPlanSeat(seat.id, draft.draftRevision)
                                : widget.api.deleteFloorPlanSeat(seat.id, draft.draftRevision))),
                    ]),
                ),
            if (busy) const LinearProgressIndicator(),
            if (error != null) Text(error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
        ]);
    }

    Future<void> _confirmDeactivate(BuildContext context) async {
        final confirmed = await showDialog<bool>(context: context, builder: (context) => AlertDialog(
            title: const Text('Delete floor plan?'),
            content: const Text('This disables floor-plan mode from the next round. Historical plans and assignments remain available.'),
            actions: [TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
                FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Disable'))],
        ));
        if (confirmed == true && state != null) await mutate(() => widget.api.deactivateFloorPlan(state!.stateRevision));
    }

    Future<void> _confirmRemoveAll(BuildContext context, AdminFloorPlanVersion draft) async {
        final confirmed = await showDialog<bool>(context: context, builder: (context) => AlertDialog(
            title: const Text('Remove all seats?'),
            content: const Text('Published seat numbers remain permanently retired. New draft seats are discarded and their provisional suffix is closed.'),
            actions: [TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
                FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Remove all'))],
        ));
        if (confirmed == true) await mutate(() => widget.api.removeAllFloorPlanSeats(draft.draftRevision));
    }

    Future<void> _addByCoordinates(BuildContext context, AdminFloorPlanVersion draft) async {
        final point = await _coordinateDialog(context, 0.5, 0.5, 'Add seat');
        if (point != null) {
            await mutate(() => widget.api.addFloorPlanSeat(draft.draftRevision, point.dx, point.dy));
        }
    }

    Future<void> _moveByCoordinates(BuildContext context, AdminFloorPlanVersion draft,
        AdminFloorPlanSeat seat) async {
        final point = await _coordinateDialog(context, seat.x, seat.y, 'Move seat ${seat.number}');
        if (point != null) {
            await mutate(() => widget.api.moveFloorPlanSeat(seat.id, draft.draftRevision, point.dx, point.dy));
        }
    }

    Future<Offset?> _coordinateDialog(BuildContext context, double initialX, double initialY, String title) async {
        final x = TextEditingController(text: initialX.toStringAsFixed(4));
        final y = TextEditingController(text: initialY.toStringAsFixed(4));
        String? validation;
        final result = await showDialog<Offset>(context: context, builder: (context) => StatefulBuilder(
            builder: (context, setDialogState) => AlertDialog(
                title: Text(title),
                content: Column(mainAxisSize: MainAxisSize.min, children: [
                    TextField(controller: x, keyboardType: const TextInputType.numberWithOptions(decimal: true),
                        decoration: const InputDecoration(labelText: 'Horizontal position (0 to 1)')),
                    TextField(controller: y, keyboardType: const TextInputType.numberWithOptions(decimal: true),
                        decoration: InputDecoration(labelText: 'Vertical position (0 to 1)', errorText: validation)),
                ]),
                actions: [
                    TextButton(onPressed: () => Navigator.pop(context), child: const Text('Cancel')),
                    FilledButton(onPressed: () {
                        final parsedX = double.tryParse(x.text.replaceFirst(',', '.'));
                        final parsedY = double.tryParse(y.text.replaceFirst(',', '.'));
                        if (parsedX == null || parsedY == null || parsedX < 0 || parsedX > 1
                                || parsedY < 0 || parsedY > 1) {
                            setDialogState(() => validation = 'Both positions must be numbers from 0 to 1.');
                            return;
                        }
                        Navigator.pop(context, Offset(parsedX, parsedY));
                    }, child: const Text('Apply')),
                ],
            ),
        ));
        x.dispose();
        y.dispose();
        return result;
    }

    static String _detail(DioException failure) => failure.error is Problem
        ? (failure.error as Problem).detail : 'The request failed. Check your connection and retry.';
}