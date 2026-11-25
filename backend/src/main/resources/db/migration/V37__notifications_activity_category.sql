ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_category_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_category_check
    CHECK (category IN (
        'APP_UPDATE', 'EXPENSE_ATTRIBUTED', 'ALLOWANCE',
        'FIXED_EXPENSE_DUE', 'VEHICLE_ALERT', 'SYSTEM', 'ACTIVITY'
    ));
