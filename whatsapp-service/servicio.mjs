// Servicio de WhatsApp de CotizaIA:
//  - Asistente: quien escribe al numero vinculado conversa con el asistente de CotizaIA (IA del backend).
//  - Cola: envia los mensajes que deja el backend (codigos de verificacion y avisos de propuestas).
// Uso: npm start (necesita un archivo .env; mira .env.example).
import makeWASocket, { DisconnectReason, fetchLatestBaileysVersion, useMultiFileAuthState } from '@whiskeysockets/baileys'
import pino from 'pino'
import { NumeroSinWhatsapp, pedirRespuesta, procesarCola, textoEntrante } from './cola.mjs'

const backendUrl = process.env.BACKEND_URL
const apiKey = process.env.WHATSAPP_SERVICE_KEY
if (!backendUrl || !apiKey) {
  console.error('Faltan BACKEND_URL o WHATSAPP_SERVICE_KEY en el archivo .env')
  process.exit(1)
}

const REVISAR_CADA_MS = 10_000
const REINTENTO_MAX_MS = 60_000

const { state, saveCreds } = await useMultiFileAuthState('auth_info')
const { version } = await fetchLatestBaileysVersion()

let socket = null
let conectado = false
let intentos = 0

async function enviar(telefono, texto) {
  const numero = telefono.replace(/\D/g, '')
  // WhatsApp indica la direccion real: en algunos paises no coincide con el numero escrito.
  const [encontrado] = await socket.onWhatsApp(numero)
  if (!encontrado?.exists) throw new NumeroSinWhatsapp(numero)
  await socket.sendMessage(encontrado.jid, { text: texto })
}

function conectar() {
  socket = makeWASocket({ auth: state, version, logger: pino({ level: 'silent' }) })
  socket.ev.on('creds.update', saveCreds)
  socket.ev.on('messages.upsert', async ({ messages, type }) => {
    // 'notify' son mensajes nuevos; 'append' es historial que llega al reconectar y no se contesta.
    if (type !== 'notify') return
    for (const mensaje of messages) {
      const texto = textoEntrante(mensaje)
      if (!texto) continue
      try {
        const respuesta = await pedirRespuesta({ backendUrl, apiKey, de: mensaje.key.remoteJid, texto })
        if (respuesta) {
          await socket.sendMessage(mensaje.key.remoteJid, { text: respuesta })
          console.log('Respuesta del asistente enviada.')
        }
      } catch (error) {
        console.error(`No se pudo responder un mensaje: ${error.message}`)
      }
    }
  })
  socket.ev.on('connection.update', ({ connection, lastDisconnect, qr }) => {
    if (qr) {
      console.error('Esta sesion no esta vinculada. Ejecuta primero: npm run vincular')
      process.exit(1)
    }
    if (connection === 'open') {
      conectado = true
      intentos = 0
      console.log('Conectado a WhatsApp. Revisando la cola del backend...')
    }
    if (connection === 'close') {
      conectado = false
      const codigo = lastDisconnect?.error?.output?.statusCode
      if (codigo === DisconnectReason.loggedOut) {
        console.error('WhatsApp cerro la sesion. Borra auth_info y ejecuta: npm run vincular')
        process.exit(1)
      }
      intentos += 1
      const espera = Math.min(1000 * 2 ** intentos, REINTENTO_MAX_MS)
      console.log(`Conexion perdida (codigo ${codigo}). Reintentando en ${espera / 1000} s...`)
      setTimeout(conectar, espera)
    }
  })
}

async function revisar() {
  // Solo se reclama con WhatsApp conectado: reclamar sin poder enviar gastaria los intentos de cada mensaje.
  if (conectado) {
    try {
      const enviados = await procesarCola({ backendUrl, apiKey, enviar })
      if (enviados) console.log(`Mensajes enviados: ${enviados}`)
    } catch (error) {
      console.error(`No se pudo revisar la cola: ${error.message}`)
    }
  }
  setTimeout(revisar, REVISAR_CADA_MS)
}

conectar()
revisar()
