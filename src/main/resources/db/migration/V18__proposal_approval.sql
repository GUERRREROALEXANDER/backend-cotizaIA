--
-- Record human approval separately from lifecycle status.
-- Quote edits invalidate this timestamp before an offer can be sent.
--
ALTER TABLE proposals ADD COLUMN approved_at TIMESTAMP WITH TIME ZONE;
