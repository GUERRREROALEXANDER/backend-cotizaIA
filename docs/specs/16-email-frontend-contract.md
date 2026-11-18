# Spec 16b - Email, frontend contract
- Register response may include `emailVerificationRequired: boolean`. When true, show "Revisa tu correo para activar la
  cuenta" instead of entering the app, with a "Reenviar correo" button (POST /api/auth/resend-verification {email}).
- Login 403 `{"error":"Email not verified"}` -> show the same message + resend button.
- Route `/verify-email?token=...` (the app uses hash or path routing: follow the existing router): POSTs
  /api/auth/verify-email {token}; success -> "Cuenta verificada" + button to the login; 400 -> "El enlace venció o ya
  se usó" + resend form. Never log the token.
