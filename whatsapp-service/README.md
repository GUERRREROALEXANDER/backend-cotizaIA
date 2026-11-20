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

## Puesta en marcha (un solo paso)

Doble clic en **`iniciar.cmd`**, o en una terminal:

```powershell
cd whatsapp-service
npm install      # solo la primera vez (iniciar.cmd lo hace solo)
npm run iniciar
```

La primera vez **solo pide el número que va a contestar** (la SIM Claro), con indicativo: `+57 300 123 4567`.
Todo lo demás lo hace el instalador:

1. Crea el `.env`, con el backend de Render ya puesto y una clave del servicio generada al azar.
2. Comprueba que el backend acepte esa clave. Si no, explica qué falta, por ejemplo:
   *"En Render > backend-cotizaia > Environment agregue `WHATSAPP_SERVICE_KEY = …`"*. Luego espera solo hasta que
   el backend esté listo.
3. Muestra un **código de 8 caracteres**. En el teléfono con la SIM Claro: WhatsApp > Dispositivos vinculados >
   Vincular un dispositivo > *"Vincular con el número de teléfono"*, y se escribe el código.
4. Arranca el asistente. Deje la ventana abierta.

Las siguientes veces no pregunta nada: solo arranca. Para usar otro número: `npm run iniciar -- --cambiar-numero`.

En el backend (Render) solo hacen falta `WHATSAPP_SERVICE_KEY`, que el instalador muestra, y las llaves de IA que ya
existen (`GEMINI_API_KEY`, `GROQ_API_KEY`). El link que envía el asistente es por defecto el frontend
(`APP_PUBLIC_URL`); para otro, `WHATSAPP_SITE_URL`. Sin IA disponible, el asistente responde un mensaje fijo con el
link.

La sesión de WhatsApp queda en `auth_info/` y la clave en `.env`. Las dos son secretas y ya están en `.gitignore`.
## Probar

- **Asistente:** desde otro teléfono escribe al número Claro *"hola"* y luego *"quiero una página web"*. Debe
  preguntarte por tu negocio, no repetir el saludo y, si preguntas el precio, no dar cifras.
- **Código:** en CotizaIA, `POST /api/auth/me/whatsapp/verification` con `{"phone":"+573XXXXXXXXX"}`. Llega el
  código; envíalo con `POST /api/auth/me/whatsapp/verify` y `{"code":"123456"}`.
- **Pruebas automáticas:** `npm test`, sin WhatsApp ni backend reales.
- **Vincular a mano** (si lo prefiere con QR): `npm run vincular`.

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
