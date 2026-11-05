# Estimation eval (synthetic seed corpus)

Provider family: `stub`  
Memory: 24 train proposals. Inputs: 6 holdout client briefs through `POST /api/quotations`.

| | MAE (hours per item) |
|---|---|
| Fixed catalog | 21.00 |
| Agent (memory + fallback) | 10.00 |
| Pass line (0.70 x catalog) | 14.70 |

Improvement: **52%** lower error than the catalog.

| Project | Requirement | Approved | Catalog | Agent | Source | Citations |
|---|---|---|---|---|---|---|
| P07 | Sitio web informativo | 34 | 30 | 46.0 | MEMORY | 5 |
| P07 | Integracion WhatsApp | 26 | 10 | 6.0 | MEMORY | 5 |
| P12 | Sitio web informativo | 63 | 30 | 46.0 | MEMORY | 5 |
| P12 | Blog y gestor de contenidos | 18 | 12 | 16.0 | MEMORY | 5 |
| P14 | Sitio web informativo | 72 | 30 | 46.0 | MEMORY | 5 |
| P14 | Integracion WhatsApp | 6 | 10 | 6.0 | MEMORY | 5 |
| P14 | Panel administrativo | 46 | 40 | 48.0 | MEMORY | 4 |
| P24 | Tienda en linea | 41 | 60 | 40.0 | MEMORY | 5 |
| P24 | Panel administrativo | 44 | 40 | 48.0 | MEMORY | 4 |
| P29 | Identidad de marca | 71 | 40 | 72.0 | MEMORY | 5 |
| P29 | Sitio web informativo | 81 | 30 | 64.0 | MEMORY | 5 |
| P30 | Identidad de marca | 22 | 40 | 27.0 | MEMORY | 5 |
| P30 | Sitio web informativo | 69 | 30 | 46.0 | MEMORY | 5 |
