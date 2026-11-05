-- Grounded proposal drafting (spec 05): the AI/template text is stored encrypted so approval can
-- regenerate the same proposal PDF instead of dropping the drafted body.
ALTER TABLE proposals ADD COLUMN drafted_content TEXT;
