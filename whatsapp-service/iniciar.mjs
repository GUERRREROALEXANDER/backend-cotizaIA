// Todo en uno: la primera vez pide el numero que va a contestar, crea el .env, vincula el numero con un codigo y
// arranca el servicio. Las siguientes veces solo arranca.
//   npm run iniciar                     -> uso normal
//   npm run iniciar -- --cambiar-numero -> olvida el numero vinculado y pide uno nuevo
import { randomBytes } from 'node:crypto'
import { rmSync } from 'node:fs'
import { stdin, stdout } from 'node:process'
import { createInterface } from 'node:readline/promises'
import { BACKEND_POR_DEFECTO, EXPLICACION, escribirEnv, interpretarNumero, leerEnv, revisarBackend } from './configuracion.mjs'
import { CARPETA_SESION, numeroVinculado, soloDigitos, vincular } from './whatsapp.mjs'

const consola = createInterface({ input: stdin, output: stdout })
const env = leerEnv()

console.log('\n=== Asistente de WhatsApp de CotizaIA ===\n')

if (process.argv.includes('--cambiar-numero')) {
  const seguro = await consola.question(
    `Se desvinculara ${numeroVinculado() ? '+' + numeroVinculado() : 'el numero actual'}. ¿Continuar? (s/n) `)
  if (!/^s/i.test(seguro.trim())) process.exit(0)
  rmSync(CARPETA_SESION, { recursive: true, force: true })
  delete env.WHATSAPP_NUMBER
}

// 1. El numero que va a contestar: lo unico que hay que escribir.
let numero = soloDigitos(env.WHATSAPP_NUMBER) ?? numeroVinculado()
while (!numero) {
  const leido = interpretarNumero(await consola.question(
    'Numero de WhatsApp que va a contestar (ej. +57 300 123 4567): '))
  if (!leido) {
    console.log('  Ese numero no es valido. Escribalo con el indicativo del pais, por ejemplo +57 300 123 4567.')
    continue
  }
  const visible = `+${leido.digitos}`
  const ok = await consola.question(`  Se usara ${visible}${leido.colombiaSupuesta ? ' (celular de Colombia)' : ''}. ¿Es correcto? (s/n) `)
  if (/^s/i.test(ok.trim())) numero = leido.digitos
}

// 2. Backend y clave: con valores por defecto, para no tener que escribir nada mas.
const backendUrl = env.BACKEND_URL || BACKEND_POR_DEFECTO
let apiKey = env.WHATSAPP_SERVICE_KEY
const claveNueva = !apiKey
if (claveNueva) apiKey = randomBytes(32).toString('hex')
escribirEnv({ WHATSAPP_NUMBER: `+${numero}`, BACKEND_URL: backendUrl, WHATSAPP_SERVICE_KEY: apiKey })

// 3. Comprobar que el backend acepta la clave. Si no, explicar que hacer y esperar a que quede listo.
console.log(`\nRevisando el backend (${backendUrl})...`)
let estado = await revisarBackend({ backendUrl, apiKey })
if (estado !== 'ok') {
  console.log(`\n  ${EXPLICACION[estado]}`)
  if (estado === 'sin-clave' || estado === 'clave-incorrecta' || claveNueva) {
    console.log('\n  En Render > backend-cotizaia > Environment agregue (o cambie) esta variable y guarde:\n')
    console.log(`      WHATSAPP_SERVICE_KEY = ${apiKey}\n`)
    console.log('  Esta clave ya quedo guardada en whatsapp-service/.env. Es secreta: no la comparta.')
  }
  console.log('\n  Sigo revisando cada 20 segundos; continuo solo cuando el backend responda bien (Ctrl+C para salir).')
  while (estado !== 'ok') {
    await new Promise((espera) => setTimeout(espera, 20_000))
    estado = await revisarBackend({ backendUrl, apiKey })
  }
}
console.log('Backend listo: acepta la clave del servicio.')

// 4. Vincular el numero, solo la primera vez.
const vinculado = numeroVinculado()
if (vinculado && vinculado !== numero) {
  console.log(`\nAtencion: la sesion guardada es de +${vinculado}, no de +${numero}.`)
  console.log('Si quiere usar +' + numero + ', ejecute: npm run iniciar -- --cambiar-numero\n')
  numero = vinculado
  escribirEnv({ WHATSAPP_NUMBER: `+${numero}`, BACKEND_URL: backendUrl, WHATSAPP_SERVICE_KEY: apiKey })
}
if (!vinculado) {
  console.log(`\nVinculando +${numero}...`)
  for (;;) {
    try {
      await vincular({ telefono: numero })
      break
    } catch (error) {
      console.log(`  ${error.message}`)
      rmSync(CARPETA_SESION, { recursive: true, force: true })
      const otra = await consola.question('  ¿Intentar de nuevo con un codigo nuevo? (s/n) ')
      if (!/^s/i.test(otra.trim())) process.exit(1)
    }
  }
  console.log(`Numero +${numero} vinculado.`)
}
consola.close()

// 5. Arrancar el servicio con esta configuracion.
process.env.BACKEND_URL = backendUrl
process.env.WHATSAPP_SERVICE_KEY = apiKey
console.log(`\nIniciando el asistente en +${numero}. Deje esta ventana abierta; Ctrl+C para detenerlo.\n`)
await import('./servicio.mjs')
