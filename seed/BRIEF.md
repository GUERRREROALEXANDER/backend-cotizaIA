# Task: write Spanish prose for synthetic past proposals

You write ONE JSON file per project: `content/<projectId>.json`, for the project ids listed in your message.
Read `manifest.json` (same folder) for each project's sector, city, channel and items with their `drivers`.
Never write outside `content/`. No other files, no scripts.

## What each file contains
```json
{
  "projectId": "P07",
  "clientName": "fictional business name (sector + city flavour, invented)",
  "contactName": "fictional first + last name",
  "brief": "the client's ORIGINAL message, 350-1200 characters, informal Colombian Spanish",
  "context": "2-3 professional sentences: who the client is and what they need",
  "items": [{"itemId": "P07-I1", "scope": "2-4 sentences describing that deliverable"}],
  "assumptions": ["2-5 short bullets"],
  "exclusions": ["2-5 short bullets"]
}
```
- `items`: exactly the manifest items, same `itemId`s, same order.
- `brief` style by `channel`: WHATSAPP = short chatty lines; EMAIL = greeting + paragraphs + sign-off; WEB_FORM = a filled "describe your project" box.

## Hard rules (a script checks all of them; a failure means the file is rejected)
1. Every driver must appear in BOTH the `brief` AND that item's `scope`:
   - numbers as DIGITS followed by the noun: `8 paginas`/`8 páginas`, `300 productos`, `3 sedes`, `2 idiomas`, `5 modulos`/`5 módulos`, `3 roles` (editor roles), `12 paginas` (SEO pages to optimize)
   - `diseno_a_medida: true` → say "diseño a medida"; `false` → say "plantilla"
   - `migracion_desde_excel: true` → mention "Excel"; `sincroniza_google_calendar: true` → "Google Calendar"
   - `pasarelas` → name each one (PSE, Wompi, PayU, Nequi)
   - WhatsApp `tipo`: "botón de chat" or "bot" with automatic notifications
   - brand `alcance`: "solo logo" → "logo"; "logo y manual de marca" → "manual de marca"; "manual completo con papeleria" → "papelería"
2. NEVER write: horas, hora, $, COP, pesos, precio, valor, costo, tarifa. No amounts of money, no durations in hours.
3. Names are invented. No real companies, no real people.
4. Valid JSON, UTF-8, Spanish accents allowed.

## Example (illustrative project, NOT in the manifest)
Drivers: Sitio web informativo {paginas: 6, diseno_a_medida: false}; Integracion WhatsApp {tipo: "boton de chat"}; channel WHATSAPP.
```json
{
  "projectId": "PXX",
  "clientName": "Panadería La Espiga de Oro",
  "contactName": "Marleny Ortiz",
  "brief": "Hola buenas tardes 👋\nTengo una panadería en Pasto y quiero una página web sencilla.\nSerían como 6 páginas: inicio, quiénes somos, productos, pedidos, galería y contacto.\nNo necesito nada muy elaborado, si hay una plantilla bonita está bien.\nTambién quiero un botón de chat de WhatsApp para que la gente nos escriba directo.\nQuedo atenta, gracias!",
  "context": "Panadería La Espiga de Oro es un negocio familiar de Pasto que hoy recibe pedidos solo por teléfono. Busca presencia web básica y un canal directo de contacto.",
  "items": [
    {"itemId": "PXX-I1", "scope": "Sitio informativo de 6 páginas construido sobre una plantilla adaptada a la marca: inicio, quiénes somos, productos, pedidos, galería y contacto."},
    {"itemId": "PXX-I2", "scope": "Botón de chat de WhatsApp visible en todas las páginas que abre la conversación con el número del negocio."}
  ],
  "assumptions": ["El cliente entrega fotos y textos.", "El dominio y el hosting los contrata el cliente."],
  "exclusions": ["Venta en línea o pagos.", "Fotografía profesional."]
}
```

## Done
After writing your files, run: `python3 tools/check_content.py <your ids>` and fix every FAIL line until it prints
`N/N projects pass`. Partial work beats nothing: write each file as soon as it is ready, then check.
Your final message: the checker's last line, nothing else.
