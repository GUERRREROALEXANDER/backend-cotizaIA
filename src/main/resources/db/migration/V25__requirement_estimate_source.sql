--
-- Estimation provenance (spec 04): where a requirement's hours came from and which
-- past items justified them. The source is a small enum name in plaintext; citations
-- are a JSON array string encrypted like every other agency-visible value.
--
ALTER TABLE extracted_requirements ADD COLUMN estimate_source VARCHAR(10);

ALTER TABLE extracted_requirements ADD COLUMN estimate_citations TEXT;
