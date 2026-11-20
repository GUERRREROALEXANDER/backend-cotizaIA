// Vincula el numero que enviara los mensajes, como un "dispositivo vinculado" de WhatsApp.
// Normalmente no hace falta: "npm run iniciar" lo hace solo. Uso manual:
//   npm run vincular                      -> muestra un QR para escanear
//   npm run vincular -- +573001234567     -> muestra un codigo de 8 caracteres para escribir en el telefono
import { soloDigitos, vincular } from './whatsapp.mjs'

const telefono = process.argv[2] ? soloDigitos(process.argv[2]) : undefined
if (process.argv[2] && !telefono) {
  console.error('Escriba el numero con indicativo de pais, por ejemplo: npm run vincular -- +573001234567')
  process.exit(1)
}

try {
  await vincular({ telefono })
  console.log('Vinculado. La sesion quedo guardada en auth_info.')
  process.exit(0)
} catch (error) {
  console.error(error.message)
  process.exit(1)
}
