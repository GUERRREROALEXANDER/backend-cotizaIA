// Vincula el numero que enviara los mensajes, como un "dispositivo vinculado" de WhatsApp.
// Uso:
//   npm run vincular                      -> muestra un QR para escanear
//   npm run vincular -- +573001234567     -> muestra un codigo de 8 caracteres para escribir en el telefono
import makeWASocket, { DisconnectReason, fetchLatestBaileysVersion, useMultiFileAuthState } from '@whiskeysockets/baileys'
import pino from 'pino'
import qrcode from 'qrcode-terminal'

const telefono = process.argv[2]?.replace(/\D/g, '')
if (process.argv[2] && !/^[1-9]\d{7,14}$/.test(telefono)) {
  console.error('Escribe el numero con indicativo de pais, por ejemplo: npm run vincular -- +573001234567')
  process.exit(1)
}

const { state, saveCreds } = await useMultiFileAuthState('auth_info')
const { version } = await fetchLatestBaileysVersion()
let codigoPedido = false

function conectar() {
  const socket = makeWASocket({ auth: state, version, logger: pino({ level: 'silent' }) })
  socket.ev.on('creds.update', saveCreds)
  socket.ev.on('connection.update', async ({ connection, lastDisconnect, qr }) => {
    if (qr && telefono) {
      // Con numero se usa el codigo de vinculacion: sirve cuando no hay otra pantalla para escanear el QR.
      if (!codigoPedido) {
        codigoPedido = true
        const codigo = await socket.requestPairingCode(telefono)
        console.log('En el telefono: WhatsApp > Dispositivos vinculados > Vincular un dispositivo >')
        console.log('"Vincular con el numero de telefono" y escribe este codigo:\n')
        console.log(`    ${codigo.slice(0, 4)}-${codigo.slice(4)}\n`)
      }
    } else if (qr) {
      console.clear()
      console.log('En el telefono: WhatsApp > Dispositivos vinculados > Vincular un dispositivo\n')
      qrcode.generate(qr, { small: true })
    }
    if (connection === 'open') {
      console.log('Vinculado. La sesion quedo guardada en auth_info.')
      process.exit(0)
    }
    if (connection === 'close') {
      const codigo = lastDisconnect?.error?.output?.statusCode
      // WhatsApp cierra la conexion justo despues de vincular y espera que se vuelva a abrir.
      if (codigo === DisconnectReason.restartRequired) return conectar()
      const motivo = codigo === DisconnectReason.loggedOut
        ? 'La sesion fue cerrada. Borra la carpeta auth_info y vuelve a intentarlo.'
        : `Se cerro la conexion (codigo ${codigo}). Vuelve a ejecutar el comando.`
      console.log(motivo)
      process.exit(1)
    }
  })
}

conectar()
