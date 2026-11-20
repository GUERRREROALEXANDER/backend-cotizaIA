// Vinculacion del numero que contestara, compartida por iniciar.mjs y vincular.mjs.
import makeWASocket, { DisconnectReason, fetchLatestBaileysVersion, useMultiFileAuthState } from '@whiskeysockets/baileys'
import { existsSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import pino from 'pino'
import qrcode from 'qrcode-terminal'

const require = createRequire(import.meta.url)
const QRCode = require('qrcode-terminal/vendor/QRCode')
const QRErrorCorrectLevel = require('qrcode-terminal/vendor/QRCode/QRErrorCorrectLevel')

export const CARPETA_SESION = 'auth_info'
export const PAGINA_QR = 'vincular-qr.html'

// El QR como SVG, para verlo en el navegador: la terminal a veces lo deforma y no se deja escanear.
export function qrSvg(texto) {
  const qr = new QRCode(-1, QRErrorCorrectLevel.L)
  qr.addData(texto)
  qr.make()
  const n = qr.getModuleCount()
  let cuadros = ''
  for (let fila = 0; fila < n; fila += 1) {
    for (let col = 0; col < n; col += 1) {
      if (qr.isDark(fila, col)) cuadros += `M${col + 4} ${fila + 4}h1v1h-1z`
    }
  }
  const lado = n + 8
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${lado} ${lado}" shape-rendering="crispEdges">`
    + `<rect width="${lado}" height="${lado}" fill="#fff"/><path d="${cuadros}" fill="#000"/></svg>`
}

// Pagina que se recarga sola: WhatsApp cambia el QR cada ~20 segundos.
function escribirPaginaQr(qr) {
  writeFileSync(PAGINA_QR, `<!doctype html><html lang="es"><head><meta charset="utf-8">
<meta http-equiv="refresh" content="3"><title>Vincular WhatsApp</title>
<style>body{font-family:system-ui,sans-serif;background:#f4f4f4;color:#111;text-align:center;margin:0;padding:24px}
.qr{width:min(80vw,420px);background:#fff;padding:12px;border-radius:12px;box-shadow:0 2px 12px #0002}</style></head>
<body><h2>Vincular el número del asistente de CotizaIA</h2>
<p>En el teléfono: WhatsApp &gt; Dispositivos vinculados &gt; Vincular un dispositivo, y escanee:</p>
<div class="qr">${qrSvg(qr)}</div>
<p>El código cambia solo cada pocos segundos. Esta página se actualiza sola.</p></body></html>`)
}

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
export async function vincular({ telefono, pagina = false } = {}) {
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
          if (pagina) escribirPaginaQr(qr)
          console.clear()
          console.log('En el telefono: WhatsApp > Dispositivos vinculados > Vincular un dispositivo\n')
          qrcode.generate(qr, { small: true })
        }
        if (connection === 'open') {
          // Se da un momento para que WhatsApp termine de guardar las credenciales.
          await new Promise((espera) => setTimeout(espera, 1500))
          if (pagina) rmSync(PAGINA_QR, { force: true })
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
