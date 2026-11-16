String assignmentActionReasonText(String? reason, {int? requiredTokens}) => switch (reason) {
    'ALREADY_OCCUPIES_DATE' => 'You already have a seat for this day.',
    'CAPACITY_STATE_UNAVAILABLE' => 'Booking information is currently unavailable.',
    'CAPACITY_UNAVAILABLE' => 'No compatible capacity is currently available.',
    'INSUFFICIENT_POST_ALLOCATION_FUNDS' when requiredTokens != null =>
        'This booking requires $requiredTokens tokens. You do not have enough '
            'relinquishment credits or predecessor tokens available.',
    'INSUFFICIENT_POST_ALLOCATION_FUNDS' =>
        'You do not have enough relinquishment credits or predecessor tokens for this booking.',
    'ORIGINAL_UNSUCCESSFUL_BID' =>
        'Open booking is unavailable because your original bid was unsuccessful.',
    'ORIGINAL_PERIOD_ONLY' =>
        'You can only rebook the same period as your relinquished assignment.',
    'LATER_BOOKING_RELINQUISHED' =>
        'You already relinquished a later booking for this day.',
    'REFUND_WINDOW_ENDED' =>
        'This booking can no longer be relinquished because its funding round has closed.',
    'TARGET_DATE_ENDED' => 'This day has ended.',
    _ => 'This action is currently unavailable.',
};