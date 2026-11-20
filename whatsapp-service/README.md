# Servicio de WhatsApp de CotizaIA

Conecta un número de WhatsApp (una SIM dedicada) con el backend de CotizaIA. Hace tres cosas:

1. **Asistente de CotizaIA.** Quien escribe al número conversa con un asistente con IA (la misma cadena Gemini →
   Groq del backend, con Circuit Breaker). Saluda, explica cómo funciona CotizaIA, ayuda a describir el proyecto con
   1 o 2 preguntas a la vez, recuerda la conversación y al final envía el link para recibir la propuesta. **Nunca da
   precios ni fechas.** Su comportamiento está en
   `src/main/resources/whatsapp/asistente-prompt.txt`; `[ENLACE DE COTIZAIA]` se reemplaza por `WHATSAPP_SITE_URL`.
2. **Código de verificación.** Un usuario de CotizaIA registra su WhatsApp, recibe un código de 6 dígitos y lo
   escribe en la app para demostrar que el número es suyo.
3. **Avisos de propuestas.** El dueño de la agencia, con su número verificado, recibe un mensaje cuando una
   propuesta queda lista para revisar, el cliente la acepta, la rechaza o quiere negociar (patrón Observer).

```
Cliente ──WhatsApp──▶ número Claro ──▶ servicio.mjs ──POST /api/whatsapp/bot/reply──▶ backend
                                         │◀──────── { reply: "¿Para qué negocio es la página?" } ◀┘
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

   - Backend: `WHATSAPP_SERVICE_KEY`, `WHATSAPP_SITE_URL` (el link que envía el asistente) y las llaves de IA
     (`LLM_CHAIN`, `GEMINI_API_KEY`, `GROQ_API_KEY`). Sin IA disponible el asistente responde un mensaje fijo con el link.
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

- **Asistente:** desde otro teléfono escribe al número Claro *"hola"* y luego *"quiero una página web"*. Debe
  preguntarte por tu negocio, no repetir el saludo y, si preguntas el precio, no dar cifras.
- **Código:** en CotizaIA, `POST /api/auth/me/whatsapp/verification` con `{"phone":"+573XXXXXXXXX"}`. Llega el
  código; envíalo con `POST /api/auth/me/whatsapp/verify` y `{"code":"123456"}`.
- **Pruebas automáticas:** `npm test`, sin WhatsApp ni backend reales.

## Límites que protegen el número

| Qué | Límite |
| --- | --- |
| Respuestas del asistente por remitente | 30 por hora (evita bucles con otros bots) |
| Memoria de la conversación | Últimos 10 intercambios; se olvida tras 30 min sin mensajes (solo en RAM) |
| Links en las respuestas | Solo `WHATSAPP_SITE_URL`; cualquier otro se reemplaza (anti-phishing) |
| Códigos de verificación | 1 por minuto y 5 por hora, por usuario y por número |
| Intentos con un código | 5; después el código se anula |
| Vigencia de un código | 10 minutos |
| Reintentos de un mensaje de la cola | 3, con 1 minuto entre cada uno |
| Pausa entre mensajes enviados | 1,5 s |

No dejes dos copias del servicio con la misma sesión `auth_info` abiertas a la vez: WhatsApp cierra una de las dos.
