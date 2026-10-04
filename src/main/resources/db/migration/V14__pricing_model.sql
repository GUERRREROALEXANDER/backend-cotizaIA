--
-- Agencies select the default pricing strategy while proposals retain the
-- strategy used for their provisional quote (project.txt section 2).
--
ALTER TABLE agencies ADD COLUMN pricing_model VARCHAR(20) NOT NULL DEFAULT 'HOURLY';
ALTER TABLE agencies ADD CONSTRAINT ck_agencies_pricing_model
    CHECK (pricing_model IN ('FIXED', 'HOURLY', 'PHASED'));

ALTER TABLE proposals ADD COLUMN pricing_model VARCHAR(20);
ALTER TABLE proposals ADD CONSTRAINT ck_proposals_pricing_model
    CHECK (pricing_model IN ('FIXED', 'HOURLY', 'PHASED'));
