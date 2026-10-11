-- =====================================================================
-- ClassSync Migration V4
-- Description: Enforce uniqueness of schedule occurrences by source timetable entry and date.
-- Note: Standard SQL and MySQL InnoDB allow multiple NULL values in unique indexes,
-- which permits future ad-hoc/extra classes (where timetable_entry_id IS NULL) on the same date.
-- =====================================================================

ALTER TABLE schedule_occurrences
ADD CONSTRAINT uk_schedule_occurrences_entry_date UNIQUE (timetable_entry_id, schedule_date);
