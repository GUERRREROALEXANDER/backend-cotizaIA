// Habla con la cola (outbox) del backend de CotizaIA. No sabe nada de WhatsApp: recibe la funcion que envia.

export class NumeroSinWhatsapp extends Error {}

const PAUSA_ENTRE_MENSAJES_MS = 1500

function headers(apiKey) {
  return { 'X-API-Key': apiKey, 'Content-Type': 'application/json' }
}

// Render puede tardar en despertar; una cotizacion completa con la IA, todavia mas.
const ESPERA_MS = 90_000
const ESPERA_RESPUESTA_MS = 240_000

async function pedir(url, apiKey, body, esperaMs = ESPERA_MS) {
  const respuesta = await fetch(url, {
    method: 'POST',
    headers: headers(apiKey),
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(esperaMs),
  })
  if (!respuesta.ok) throw new Error(`El backend respondio ${respuesta.status}`)
  return respuesta
}

// Pregunta al backend que contestar a un mensaje recibido. Devuelve el texto, o null si el bot no debe responder.
// "telefono" es el numero real de quien escribe: con el, el backend reconoce a un usuario que verifico su numero.
export async function pedirRespuesta({ backendUrl, apiKey, de, telefono, texto }) {
  const url = `${backendUrl.replace(/\/+$/, '')}/api/whatsapp/bot/reply`
  const cuerpo = { from: de, text: texto.slice(0, 2000), ...(telefono ? { phone: telefono } : {}) }
  const { reply } = await (await pedir(url, apiKey, cuerpo, ESPERA_RESPUESTA_MS)).json()
  return reply ?? null
}

// El numero de quien escribe (solo digitos, con indicativo) o null. WhatsApp a veces identifica el chat con un
// alias anonimo (@lid) y deja el numero real en remoteJidAlt; se toma el primero que sea un numero (@s.whatsapp.net).
export function telefonoDe(mensaje) {
  const key = mensaje?.key ?? {}
  for (const jid of [key.remoteJid, key.remoteJidAlt, key.senderPn]) {
    if (typeof jid === 'string' && jid.endsWith('@s.whatsapp.net')) {
      const digitos = jid.split('@')[0].split(':')[0]
      if (/^[1-9]\d{7,14}$/.test(digitos)) return digitos
    }
  }
  return null
}

// El texto de un mensaje recibido, o null si no es para el bot (propio, de grupo, estado, canal o sin texto).
export function textoEntrante(mensaje) {
  const jid = mensaje?.key?.remoteJid
  if (!jid || mensaje.key.fromMe) return null
  if (jid.endsWith('@g.us') || jid.endsWith('@broadcast') || jid.endsWith('@newsletter')) return null
  const texto = mensaje.message?.conversation ?? mensaje.message?.extendedTextMessage?.text
  return texto?.trim() ? texto.trim() : null
}

export async function procesarCola({ backendUrl, apiKey, enviar, pausaMs = PAUSA_ENTRE_MENSAJES_MS }) {
  const base = `${backendUrl.replace(/\/+$/, '')}/api/whatsapp/outbox`
  const { items } = await (await pedir(`${base}/claim`, apiKey)).json()
  let enviados = 0
  for (const mensaje of items) {
    try {
      await enviar(mensaje.phone, mensaje.body)
      await pedir(`${base}/${mensaje.id}/sent`, apiKey)
      enviados += 1
    } catch (error) {
      if (error instanceof NumeroSinWhatsapp) {
        await pedir(`${base}/${mensaje.id}/failed`, apiKey, { error: 'number is not on WhatsApp' })
      } else {
        // Sin avisar al backend: el mensaje vuelve a la cola en un minuto, hasta tres intentos.
        console.error(`No se pudo enviar el mensaje ${mensaje.id}: ${error.message}`)
      }
    }
    if (pausaMs) await new Promise((listo) => setTimeout(listo, pausaMs))
  }
  return enviados
}
