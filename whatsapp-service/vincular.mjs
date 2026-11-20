// Vincula el numero que enviara los mensajes, como un "dispositivo vinculado" de WhatsApp.
// Normalmente no hace falta: "npm run iniciar" lo hace solo. Uso manual:
//   npm run vincular                      -> QR en la terminal y en vincular-qr.html (se abre en el navegador)
//   npm run vincular -- +573001234567     -> un codigo de 8 caracteres para escribir en el telefono
import { exec } from 'node:child_process'
import { resolve } from 'node:path'
import { PAGINA_QR, numeroVinculado, soloDigitos, vincular } from './whatsapp.mjs'

const telefono = process.argv[2] ? soloDigitos(process.argv[2]) : undefined
if (process.argv[2] && !telefono) {
  console.error('Escriba el numero con indicativo de pais, por ejemplo: npm run vincular -- +573001234567')
  process.exit(1)
}

if (!telefono) {
  // Abre la pagina del QR en cuanto exista; se recarga sola con cada QR nuevo.
  setTimeout(() => {
    const ruta = resolve(PAGINA_QR)
    const abrir = process.platform === 'win32' ? `start "" "${ruta}"`
      : process.platform === 'darwin' ? `open "${ruta}"` : `xdg-open "${ruta}"`
    exec(abrir)
    console.log(`\nQR tambien en el navegador: ${ruta}`)
  }, 4000)
}

try {
  await vincular({ telefono, pagina: !telefono })
  console.log(`Vinculado: +${numeroVinculado()}. La sesion quedo guardada en auth_info.`)
  process.exit(0)
} catch (error) {
  console.error(error.message)
  process.exit(1)
}
