// Vinculacion del numero que contestara, compartida por iniciar.mjs y vincular.mjs.
import makeWASocket, { DisconnectReason, fetchLatestBaileysVersion, useMultiFileAuthState } from '@whiskeysockets/baileys'
import { existsSync, readFileSync } from 'node:fs'
import pino from 'pino'
import qrcode from 'qrcode-terminal'

export const CARPETA_SESION = 'auth_info'

// El numero con indicativo, solo digitos (573001234567), o null si no es valido.
export function soloDigitos(numero) {
  const digitos = String(numero ?? '').replace(/\D/g, '')
  return /^[1-9]\d{7,14}$/.test(digitos) ? digitos : null
}

// El numero que quedo vinculado en auth_info, o null si todavia no hay sesion.
export function numeroVinculado() {
  const archivo = `${CARPETA_SESION}/creds.json`
  if (!existsSync(archivo)) return null
  try {
    const creds = JSON.parse(readFileSync(archivo, 'utf8'))
    if (!creds.registered && !creds.me?.id) return null
    return creds.me?.id?.split(/[:@]/)[0] ?? null
  } catch {
    return null
  }
}

// Vincula el numero como "dispositivo vinculado". Con telefono muestra un codigo de 8 caracteres; sin el, un QR.
// Termina cuando WhatsApp confirma la vinculacion.
export async function vincular({ telefono } = {}) {
  const { state, saveCreds } = await useMultiFileAuthState(CARPETA_SESION)
  const { version } = await fetchLatestBaileysVersion()
  let codigoPedido = false

  return new Promise((listo, fallo) => {
    function conectar() {
      const socket = makeWASocket({ auth: state, version, logger: pino({ level: 'silent' }) })
      socket.ev.on('creds.update', saveCreds)
      socket.ev.on('connection.update', async ({ connection, lastDisconnect, qr }) => {
        if (qr && telefono && !codigoPedido) {
          codigoPedido = true
          try {
            const codigo = await socket.requestPairingCode(telefono)
            console.log('\nEn el telefono con ese numero abra WhatsApp y vaya a:')
            console.log('  Dispositivos vinculados > Vincular un dispositivo > "Vincular con el numero de telefono"')
            console.log('y escriba este codigo:\n')
            console.log(`        ${codigo.slice(0, 4)}-${codigo.slice(4)}\n`)
            console.log('Esperando a que lo escriba (tiene unos minutos)...')
          } catch (error) {
            fallo(new Error(`WhatsApp no entrego el codigo: ${error.message}`))
          }
        } else if (qr && !telefono) {
          console.clear()
          console.log('En el telefono: WhatsApp > Dispositivos vinculados > Vincular un dispositivo\n')
          qrcode.generate(qr, { small: true })
        }
        if (connection === 'open') {
          // Se da un momento para que WhatsApp termine de guardar las credenciales.
          await new Promise((espera) => setTimeout(espera, 1500))
          socket.end(undefined)
          listo()
        }
        if (connection === 'close') {
          const codigo = lastDisconnect?.error?.output?.statusCode
          // WhatsApp cierra la conexion justo despues de vincular y espera que se vuelva a abrir.
          if (codigo === DisconnectReason.restartRequired) return conectar()
          fallo(new Error(codigo === DisconnectReason.loggedOut
            ? 'WhatsApp rechazo la vinculacion. Intente de nuevo.'
            : `Se cerro la conexion (codigo ${codigo}). El codigo pudo vencer; intente de nuevo.`))
        }
      })
    }
    conectar()
  })
}
