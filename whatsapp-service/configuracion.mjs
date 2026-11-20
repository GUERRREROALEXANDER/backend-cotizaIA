// Lectura y escritura del archivo .env y diagnostico de la conexion con el backend. Sin WhatsApp, para poder probarlo.
import { existsSync, readFileSync, writeFileSync } from 'node:fs'

export const BACKEND_POR_DEFECTO = 'https://backend-cotizaia.onrender.com'

export function leerEnv(ruta = '.env') {
  if (!existsSync(ruta)) return {}
  const valores = {}
  for (const linea of readFileSync(ruta, 'utf8').split(/\r?\n/)) {
    const encontrado = linea.match(/^\s*([A-Z_][A-Z0-9_]*)\s*=\s*(.*)\s*$/)
    if (encontrado) valores[encontrado[1]] = encontrado[2].replace(/^["']|["']$/g, '')
  }
  return valores
}

export function escribirEnv(valores, ruta = '.env') {
  const contenido = [
    '# Creado por "npm run iniciar". No lo suba a git: contiene la clave del servicio.',
    `WHATSAPP_NUMBER=${valores.WHATSAPP_NUMBER ?? ''}`,
    `BACKEND_URL=${valores.BACKEND_URL ?? ''}`,
    `WHATSAPP_SERVICE_KEY=${valores.WHATSAPP_SERVICE_KEY ?? ''}`,
    '',
  ].join('\n')
  writeFileSync(ruta, contenido, { encoding: 'utf8', mode: 0o600 })
}

// Pregunta al backend si acepta la clave sin tocar la cola: reportar el mensaje 0, que no existe, responde 404 solo
// cuando la clave es correcta. Devuelve 'ok', 'sin-clave', 'clave-incorrecta', 'sin-whatsapp' o 'sin-conexion'.
export async function revisarBackend({ backendUrl, apiKey }) {
  let respuesta
  try {
    respuesta = await fetch(`${backendUrl.replace(/\/+$/, '')}/api/whatsapp/outbox/0/sent`, {
      method: 'POST',
      headers: { 'X-API-Key': apiKey },
      signal: AbortSignal.timeout(90_000), // Render gratis tarda en despertar.
    })
  } catch {
    return 'sin-conexion'
  }
  if (respuesta.status === 404) return 'ok'
  if (respuesta.status === 503) return 'sin-clave'
  if (respuesta.status === 401) {
    const cuerpo = await respuesta.text().catch(() => '')
    // "Authentication required" lo responde Spring Security: el backend aun no tiene las rutas de WhatsApp.
    return cuerpo.includes('Invalid service key') ? 'clave-incorrecta' : 'sin-whatsapp'
  }
  return 'sin-conexion'
}

export const EXPLICACION = {
  'sin-clave': 'El backend no tiene WHATSAPP_SERVICE_KEY configurada.',
  'clave-incorrecta': 'El backend tiene otra WHATSAPP_SERVICE_KEY distinta a la de este .env.',
  'sin-whatsapp': 'El backend todavia no tiene la version con WhatsApp (falta fusionar el PR y que Render despliegue).',
  'sin-conexion': 'No se pudo conectar con el backend. Revise BACKEND_URL y su conexion a internet.',
}
