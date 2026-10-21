import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:file_selector/file_selector.dart';

import '../../core/api_client.dart';
import '../../core/models.dart';
import '../floor_plan/floor_plan_interactive_viewer.dart';
import '../floor_plan/floor_plan_overlay_card.dart';
import '../floor_plan/floor_plan_svg_image.dart';

class FloorPlanAdminScreen extends StatefulWidget {
    const FloorPlanAdminScreen({super.key, required this.api});
    final ApiClient api;
    @override
    State<FloorPlanAdminScreen> createState() => _FloorPlanAdminScreenState();
}

class _FloorPlanAdminScreenState extends State<FloorPlanAdminScreen> {
    AdminFloorPlan? state;
    GuestSeatPreferences? guestPreferences;
    List<GuestSeatPreference> guestDraft = const [];
    Uint8List? svg;
    int? selectedSeat;
    bool addMode = false;
    bool busy = true;
    String? error;
    String? notice;

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

    Future<void> update(AdminFloorPlan value, {bool addModeAfterUpdate = false}) async {
        final version = value.draft ?? value.pendingVersion ?? value.activeVersion;
        final bytes = version == null ? null : await widget.api.floorPlanSvg(version.svgUrl);
        GuestSeatPreferences? guests;
        if (value.activeVersion != null) {
            try {
                guests = await widget.api.guestSeatPreferences();
            } on DioException catch (failure) {
                if (failure.response?.statusCode != 404) rethrow;
            }
        }
        if (mounted) {
            setState(() {
                state = value; svg = bytes; guestPreferences = guests;
                guestDraft = guests?.seats ?? const [];
                busy = false; error = null; notice = null; selectedSeat = null; addMode = addModeAfterUpdate;
            });
        }
    }

    Future<void> upload() async {
        try {
            const group = XTypeGroup(label: 'SVG floor plans', extensions: ['svg'], mimeTypes: ['image/svg+xml']);
            final file = await openFile(acceptedTypeGroups: const [group]);
            if (file == null) return;
            await mutate(() async => widget.api.uploadFloorPlan(await file.readAsBytes()));
        }
        on DioException catch (failure) {
            if (mounted) setState(() { busy = false; error = _detail(failure); });
        }
        catch (_) {
            if (mounted) {
                setState(() {
                    busy = false;
                    error = 'The SVG file picker could not be opened. Reload the application and retry.';
                });
            }
        }
    }

    Future<void> mapTap(TapUpDetails details, Size size) async {
        final draft = state?.draft;
        if (draft == null) return;
        final x = (details.localPosition.dx / size.width).clamp(0.0, 1.0).toDouble();
        final y = (details.localPosition.dy / size.height).clamp(0.0, 1.0).toDouble();
        if (addMode) {
            await mutate(() => widget.api.addFloorPlanSeat(draft.draftRevision, x, y), keepAddMode: true);
        } else if (selectedSeat != null) {
            await mutate(() => widget.api.moveFloorPlanSeat(selectedSeat!, draft.draftRevision, x, y));
        }
    }

    Future<void> mutate(Future<AdminFloorPlan> Function() action, {bool keepAddMode = false}) async {
        final addModeAfterUpdate = keepAddMode && addMode;
        setState(() { busy = true; error = null; notice = null; });
        try { await update(await action(), addModeAfterUpdate: addModeAfterUpdate); }
        on DioException catch (failure) { if (mounted) setState(() { busy = false; error = _detail(failure); }); }
    }

    void cycleGuestSeat(int seatId) {
        final preferredExists = guestDraft.any((seat) => seat.stars == 3 && seat.id != seatId);
        setState(() {
            guestDraft = guestDraft.map((seat) {
                if (seat.id != seatId) return seat;
                final next = switch (seat.stars) {
                    0 => 1,
                    1 => 2,
                    2 when !preferredExists => 3,
                    _ => 0,
                };
                return seat.copyWith(stars: next);
            }).toList();
        });
    }

    Future<void> saveGuestPreferences() async {
        final current = guestPreferences;
        if (current == null) {
            return;
        }
        setState(() { busy = true; error = null; notice = null; });
        try {
            final saved = await widget.api.replaceGuestSeatPreferences(current, guestDraft);
            if (mounted) {
                setState(() {
                    guestPreferences = saved; guestDraft = saved.seats; busy = false;
                    notice = saved.effectiveForCurrentRound ? 'Guest preferences saved.'
                        : 'Saved. These guest preferences apply only to future rounds because cutoff has passed.';
                });
            }
        } on DioException catch (failure) {
            if (mounted) setState(() { busy = false; error = _detail(failure); });
        }
    }

    @override
    Widget build(BuildContext context) {
        final draft = state?.draft;
        final shown = draft ?? state?.pendingVersion ?? state?.activeVersion;
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
                    || state?.publicationPreview?.reason == 'PENDING_CHANGE'
                    ? null : () => _confirmPublish(context, draft),
                    child: const Text('Publish floor plan')),
                if (draft != null) FilterChip(
                    selected: addMode,
                    onSelected: busy ? null : (selected) => setState(() {
                        addMode = selected; selectedSeat = null;
                    }),
                    avatar: const Icon(Icons.add_location_alt_outlined),
                    label: const Text('Add seats on map')),
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
            const SizedBox(height: 8),
            const Text('Standard draw.io SVG exports are supported. Embedded styling and editor metadata are '
                'removed during upload.'),
            const SizedBox(height: 12),
            if (state?.publicationPreview != null)
                _PublicationCard(timing: state!.publicationPreview!, published: false),
            if (state?.publicationEffect != null)
                _PublicationCard(timing: state!.publicationEffect!, published: true),
            for (final warning in state?.warnings ?? const <String>[])
                Card(color: Theme.of(context).colorScheme.errorContainer,
                    child: ListTile(leading: const Icon(Icons.warning_amber), title: Text(warning))),
            if (draft != null) Text(addMode ? 'Add-seat mode: click repeatedly to place numbered seats; turn the toggle off when finished.'
                : selectedSeat == null ? 'Drag to pan. Hold Ctrl and use the mouse wheel to zoom, or select a marker to move it.'
                : 'Seat selected. Click the map to move it; click the marker again or use Clear selection to unselect it.'),
            if (shown != null && svg != null)
                AspectRatio(aspectRatio: shown.viewBox.width / shown.viewBox.height,
                    child: LayoutBuilder(builder: (context, mapConstraints) => Stack(children: [
                        Positioned.fill(child: FloorPlanInteractiveViewer(
                            key: ValueKey('floor-plan-${shown.id}'),
                            child: LayoutBuilder(builder: (context, constraints) => GestureDetector(
                                onTapUp: busy || draft == null || (!addMode && selectedSeat == null)
                                    ? null : (details) => mapTap(details, constraints.biggest),
                                child: Stack(children: [
                                    Positioned.fill(child: FloorPlanSvgImage(bytes: svg!,
                                        semanticsLabel: 'Sanitized office floor plan')),
                                    for (final seat in shown.seats) Positioned(
                                        left: seat.x * constraints.maxWidth - 20,
                                        top: seat.y * constraints.maxHeight - 20,
                                        child: Semantics(label: 'Seat ${seat.number}, ${seat.state}', button: true,
                                            child: IconButton.filled(
                                                style: IconButton.styleFrom(
                                                    backgroundColor: seat.state == 'PENDING_REMOVAL'
                                                        ? Theme.of(context).colorScheme.errorContainer
                                                        : Theme.of(context).colorScheme.primary,
                                                    foregroundColor: seat.state == 'PENDING_REMOVAL'
                                                        ? Theme.of(context).colorScheme.onErrorContainer
                                                        : Colors.white,
                                                ),
                                                onPressed: draft != null ? () => setState(() {
                                                    selectedSeat = selectedSeat == seat.id ? null : seat.id;
                                                    addMode = false;
                                                }) : guestPreferences?.versionId == shown.id
                                                    ? () => cycleGuestSeat(seat.id) : null,
                                                tooltip: draft == null && guestPreferences?.versionId == shown.id
                                                    ? 'Seat ${seat.number}, ${_guestStars(seat.id)} guest stars'
                                                    : selectedSeat == seat.id ? 'Unselect seat ${seat.number}'
                                                    : 'Select seat ${seat.number}',
                                                icon: Text(draft == null && guestPreferences?.versionId == shown.id
                                                    ? '${seat.number}\n${_guestStars(seat.id)}★' : '${seat.number}',
                                                    textAlign: TextAlign.center,
                                                    style: TextStyle(color: seat.state == 'PENDING_REMOVAL'
                                                        ? Theme.of(context).colorScheme.onErrorContainer
                                                        : Colors.white, fontWeight: FontWeight.bold)),
                                            )),
                                    ),
                                ]),
                            )),
                        )),
                        if (draft != null) _seatListOverlay(context, draft, mapConstraints.biggest),
                    ])),
                ),
            if (guestPreferences != null) ...[
                const Divider(height: 36),
                Text('Guest seat preferences', style: Theme.of(context).textTheme.titleLarge),
                const SizedBox(height: 4),
                const Text('This shared rating applies to anonymous reserved guests only. Employees are assigned '
                    'first using every seat; guests then receive the highest-rated unused seats. Zero stars means '
                    'least preferred, not unavailable.'),
                const SizedBox(height: 8),
                for (final seat in guestDraft) ListTile(
                    title: Text('Seat ${seat.number}'),
                    subtitle: Text(seat.stars == 0 ? 'Least preferred · still assignable'
                        : '${seat.stars} star${seat.stars == 1 ? '' : 's'}'),
                    trailing: Semantics(
                        button: true,
                        label: 'Seat ${seat.number}, ${seat.stars} stars. Change guest rating.',
                        child: OutlinedButton.icon(
                            onPressed: busy ? null : () => cycleGuestSeat(seat.id),
                            icon: Icon(seat.stars == 0 ? Icons.star_border : Icons.star),
                            label: Text('${seat.stars} / 3'),
                        ),
                    ),
                ),
                Align(alignment: Alignment.centerLeft, child: FilledButton.icon(
                    onPressed: busy || !_guestChanged ? null : saveGuestPreferences,
                    icon: const Icon(Icons.save_outlined),
                    label: const Text('Save guest preferences'),
                )),
            ],
            if (busy) const LinearProgressIndicator(),
            if (notice != null) Text(notice!, style: TextStyle(color: Theme.of(context).colorScheme.primary)),
            if (error != null) Text(error!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
        ]);
    }

    Widget _seatListOverlay(BuildContext context, AdminFloorPlanVersion draft, Size mapSize) {
        return FloorPlanOverlayCard(
            key: ValueKey('seat-list-${draft.id}'),
            mapSize: mapSize,
            title: 'Seats (${draft.seats.length})',
            rowCount: draft.seats.length,
            headerActions: [
                if (selectedSeat != null) IconButton(
                    onPressed: () => setState(() => selectedSeat = null),
                    tooltip: 'Clear seat selection',
                    icon: const Icon(Icons.deselect),
                ),
            ],
            child: ListView(padding: EdgeInsets.zero, children: [
                for (final seat in draft.seats) ListTile(
                    dense: true,
                    visualDensity: VisualDensity.compact,
                    title: Text('Seat ${seat.number}'),
                    subtitle: Text(seat.inherited ? '${seat.state} · permanent' : 'New in draft'),
                    selected: selectedSeat == seat.id,
                    onTap: () => setState(() {
                        selectedSeat = selectedSeat == seat.id ? null : seat.id;
                        addMode = false;
                    }),
                    trailing: Row(mainAxisSize: MainAxisSize.min, children: [
                        IconButton(
                            icon: const Icon(Icons.edit_location_alt_outlined),
                            tooltip: 'Move seat ${seat.number} by coordinates',
                            onPressed: busy || seat.state != 'ACTIVE' ? null
                                : () => _moveByCoordinates(context, draft, seat),
                        ),
                        IconButton(
                            icon: Icon(seat.state == 'PENDING_REMOVAL'
                                ? Icons.restore : Icons.delete_outline),
                            tooltip: seat.state == 'PENDING_REMOVAL'
                                ? 'Restore seat ${seat.number}' : 'Remove seat ${seat.number}',
                            onPressed: busy ? null : () => mutate(() => seat.state == 'PENDING_REMOVAL'
                                ? widget.api.restoreFloorPlanSeat(seat.id, draft.draftRevision)
                                : widget.api.deleteFloorPlanSeat(seat.id, draft.draftRevision)),
                        ),
                    ]),
                ),
            ]),
        );
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

    Future<void> _confirmPublish(BuildContext context, AdminFloorPlanVersion draft) async {
        final timing = state?.publicationPreview;
        if (timing == null) {
            return;
        }
        final confirmed = await showDialog<bool>(context: context, builder: (context) => AlertDialog(
            title: const Text('Publish floor plan?'),
            content: Text(_publicationMessage(timing, published: false)),
            actions: [
                TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Cancel')),
                FilledButton(onPressed: () => Navigator.pop(context, true), child: const Text('Publish')),
            ],
        ));
        if (confirmed == true) {
            await mutate(() => widget.api.publishFloorPlan(draft.draftRevision));
        }
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

    bool get _guestChanged {
        final saved = guestPreferences?.seats;
        if (saved == null || saved.length != guestDraft.length) return false;
        final byId = {for (final seat in saved) seat.id: seat.stars};
        return guestDraft.any((seat) => byId[seat.id] != seat.stars);
    }

    int _guestStars(int seatId) {
        for (final seat in guestDraft) {
            if (seat.id == seatId) return seat.stars;
        }
        return 0;
    }
}

class _PublicationCard extends StatelessWidget {
    const _PublicationCard({required this.timing, required this.published});
    final FloorPlanPublicationTiming timing;
    final bool published;
    @override
    Widget build(BuildContext context) => Card(child: ListTile(
        leading: Icon(timing.immediate ? Icons.bolt : Icons.schedule),
        title: Text(published ? 'Publication result' : 'Publication timing'),
        subtitle: Text(_publicationMessage(timing, published: published)),
    ));
}

String _publicationMessage(FloorPlanPublicationTiming timing, {required bool published}) {
    final dates = timing.targetDates.isEmpty ? '' : ' (${timing.targetDates.first.day}.'
        '${timing.targetDates.first.month}.–${timing.targetDates.last.day}.${timing.targetDates.last.month}.)';
    if (timing.immediate) {
        return '${published ? 'The floor plan now applies' : 'The floor plan will apply immediately'} to the '
            'current bidding round$dates. Its ${timing.draftSeatCapacity} seats match the round capacity, so bids, '
            'winner selection, reservations, and token accounting do not change.';
    }
    final reason = switch (timing.reason) {
        'CAPACITY_CHANGED' => 'Its ${timing.draftSeatCapacity} seats do not match the current round capacity '
            '${timing.currentRoundCapacity ?? '—'}.',
        'CUTOFF_REACHED' => 'The current round cutoff has passed.',
        'ROUND_ALREADY_USES_FLOOR_PLAN' => 'The current round already uses a floor plan.',
        'PENDING_CHANGE' => 'Another floor-plan change is already pending.',
        _ => 'There is no eligible open configuration-only round.',
    };
    return '${published ? 'The floor plan was scheduled' : 'The floor plan will be scheduled'} for the next '
        'successor round. $reason';
}