# Servicio de WhatsApp de CotizaIA

Conecta un número de WhatsApp (una SIM dedicada) con el backend de CotizaIA. Hace tres cosas:

1. **Bot de cotización.** Alguien escribe al número, por ejemplo *"quiero hacer una página web con pagos"*, y recibe
   en segundos un rango de precio aproximado y el link a la página de la agencia. El precio sale del catálogo y las
   tarifas de la agencia en CotizaIA, con el mismo `PricingService` (patrón Strategy) de las propuestas, no de la IA.
2. **Código de verificación.** Un usuario de CotizaIA registra su WhatsApp, recibe un código de 6 dígitos y lo
   escribe en la app para demostrar que el número es suyo.
3. **Avisos de propuestas.** El dueño de la agencia, con su número verificado, recibe un mensaje cuando una
   propuesta queda lista para revisar, el cliente la acepta, la rechaza o quiere negociar (patrón Observer).

```
Cliente ──WhatsApp──▶ número Claro ──▶ servicio.mjs ──POST /api/whatsapp/bot/reply──▶ backend
                                         │◀──────── { reply: "Entre $3.250.000 y ..." } ◀┘
backend ── escribe en la cola ──▶ whatsapp_messages ◀── POST /api/whatsapp/outbox/claim ── servicio.mjs ──▶ WhatsApp
```

El backend nunca llama a WhatsApp: la sesión vive en este servicio, que puede correr en un PC detrás de NAT.

> ⚠️ Usa **Baileys**, una librería no oficial. Meta puede bloquear un número que la use. Por eso va en una SIM
> dedicada (la línea Claro), nunca en tu número personal.

## Requisitos

Node 20.6 o superior.

## Puesta en marcha

1. Instala las dependencias:

   ```powershell
   cd whatsapp-service
   npm install
   ```

2. Crea la clave compartida (32+ caracteres) y ponla **igual** en los dos lados:

   ```powershell
   node -e "console.log(require('crypto').randomBytes(32).toString('hex'))"
   ```

   - Backend: variables `WHATSAPP_SERVICE_KEY`, `WHATSAPP_AGENCY_ID` (la agencia cuyo catálogo cotiza el bot) y
     `WHATSAPP_SITE_URL` (el link que envía).
   - Servicio: copia `.env.example` como `.env` y llena `BACKEND_URL` y `WHATSAPP_SERVICE_KEY`.

3. Vincula la SIM Claro (una sola vez). En el teléfono con la SIM, abre WhatsApp y ve a *Dispositivos vinculados >
   Vincular un dispositivo*. Luego elige una de estas dos formas:

   ```powershell
   npm run vincular                       # muestra un QR para escanear
   npm run vincular -- +573XXXXXXXXX      # o un código de 8 caracteres para escribir en el teléfono
   ```

   La sesión queda en `auth_info/`. Esa carpeta es secreta, porque con ella se pueden enviar mensajes como ese
   número. Ya está en `.gitignore`.

4. Arranca el servicio:

   ```powershell
   npm start
   ```

## Probar

- **Bot:** desde otro teléfono escribe al número Claro *"quiero una página web"*. La respuesta llega con el rango de
  precio y el link.
- **Código:** en CotizaIA, `POST /api/auth/me/whatsapp/verification` con `{"phone":"+573XXXXXXXXX"}`. Llega el
  código; envíalo con `POST /api/auth/me/whatsapp/verify` y `{"code":"123456"}`.
- **Pruebas automáticas:** `npm test`, sin WhatsApp ni backend reales.

## Límites que protegen el número

| Qué | Límite |
| --- | --- |
| Respuestas del bot por remitente | 15 por hora (evita bucles con otros bots) |
| Códigos de verificación | 1 por minuto y 5 por hora, por usuario y por número |
| Intentos con un código | 5; después el código se anula |
| Vigencia de un código | 10 minutos |
| Reintentos de un mensaje de la cola | 3, con 1 minuto entre cada uno |
| Pausa entre mensajes enviados | 1,5 s |

No dejes dos copias del servicio con la misma sesión `auth_info` abiertas a la vez: WhatsApp cierra una de las dos.
