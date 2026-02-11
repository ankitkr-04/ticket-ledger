ALTER TABLE bookings 
RENAME COLUMN bumped_by_booking_id TO displaced_by_booking_id;

COMMENT ON COLUMN bookings.displaced_by_booking_id IS 'ID of the booking that displaced this one (if system cancelled/bumped)';
