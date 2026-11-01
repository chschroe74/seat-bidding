String assignmentActionReasonText(String? reason) => switch (reason) {
    'ALREADY_OCCUPIES_DATE' => 'You already have a seat for this day.',
    'CAPACITY_STATE_UNAVAILABLE' => 'Booking information is currently unavailable.',
    'CAPACITY_UNAVAILABLE' => 'No compatible capacity is currently available.',
    'ORIGINAL_UNSUCCESSFUL_BID' =>
        'Open booking is unavailable because your original bid was unsuccessful.',
    'ORIGINAL_PERIOD_ONLY' =>
        'You can only rebook the same period as your relinquished assignment.',
    'TARGET_DATE_ENDED' => 'This day has ended.',
    _ => 'This action is currently unavailable.',
};