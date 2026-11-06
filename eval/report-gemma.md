# Estimation eval (synthetic seed corpus)

Provider family: `ollama-cloud` (Gemma 4 `gemma4:31b` on Ollama Cloud; Voyage `voyage-4-lite`), run 2026-11-06  
Memory: 24 train proposals. Inputs: 6 holdout client briefs through `POST /api/quotations`.

| | MAE (hours per item) |
|---|---|
| Fixed catalog | 21.00 |
| Agent (memory + fallback) | 9.54 |
| Pass line (0.70 x catalog) | 14.70 |

Improvement: **55%** lower error than the catalog.

| Project | Requirement | Approved | Catalog | Agent | Source | Citations |
|---|---|---|---|---|---|---|
| P07 | Sitio web informativo | 34 | 30 | 66.0 | MEMORY | 5 |
| P07 | Integracion WhatsApp | 26 | 10 | 6.0 | MEMORY | 5 |
| P12 | Sitio web informativo | 63 | 30 | 76.0 | MEMORY | 5 |
| P12 | Blog y gestor de contenidos | 18 | 12 | 16.0 | MEMORY | 5 |
| P14 | Sitio web informativo | 72 | 30 | 64.0 | MEMORY | 5 |
| P14 | Integracion WhatsApp | 6 | 10 | 6.0 | MEMORY | 5 |
| P14 | Panel administrativo | 46 | 40 | 48.0 | MEMORY | 4 |
| P24 | Tienda en linea | 41 | 60 | 40.0 | MEMORY | 5 |
| P24 | Panel administrativo | 44 | 40 | 37.0 | MEMORY | 4 |
| P29 | Identidad de marca | 71 | 40 | 72.0 | MEMORY | 5 |
| P29 | Sitio web informativo | 81 | 30 | 76.0 | MEMORY | 5 |
| P30 | Identidad de marca | 22 | 40 | 48.0 | MEMORY | 5 |
| P30 | Sitio web informativo | 69 | 30 | 76.0 | MEMORY | 5 |
