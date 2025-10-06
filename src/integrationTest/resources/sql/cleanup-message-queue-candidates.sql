-- More aggressive cleanup to ensure all records are deleted
TRUNCATE TABLE message_queue_candidates RESTART IDENTITY CASCADE;
