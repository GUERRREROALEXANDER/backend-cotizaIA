# Spec 14 - Persisted price extras (Decorator)

## Why
`UrgencyDecorator` (+25%), `DiscountDecorator` (-10%) and `ExtendedWarrantyDecorator` exist and `Proposal.addExtra`
persists them, but only tests use them; the UI toggles are a browser-only preview.

## Contract
- `POST /api/proposals/{id}/extras` body `{"type": "URGENCY"|"DISCOUNT"|"EXTENDED_WARRANTY"}` -> `200 ProposalDetailResponse`
  (extras list and total updated; total = subtotal + signed extras, as `Proposal` already defines).
  Built with the real decorators on the current subtotal; amount = delta they produce.
- `DELETE /api/proposals/{id}/extras/{extraId}` -> `200 ProposalDetailResponse`, extra removed, total recomputed.
- Only while status is `IN_REVIEW` and not yet approved (`approvedAt == null`); otherwise `409`.
  Same type twice -> `409`. Unknown type -> `400`. Foreign agency / unknown id -> `404`.
- Adding or removing an extra regenerates the proposal documents like `adjustHours` does, and is undoable through the
  existing command history if that mechanism wraps `adjustHours` (follow its pattern; if it does not fit, skip undo and say so).

## Judge
Tests: each type changes the total by exactly the decorator's delta (BigDecimal compare), delete restores the previous
total, 409 after approval and on duplicate, 400 unknown type, 404 foreign agency, PDF regenerated (document updated).
Then full suite 0 failures. Out of scope: new decorator types, per-item discounts, FE.
