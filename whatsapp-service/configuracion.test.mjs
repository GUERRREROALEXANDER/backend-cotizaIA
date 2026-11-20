// Pruebas del instalador sin WhatsApp ni backend reales.
// Uso: npm test
import assert from 'node:assert/strict'
import { mkdtempSync, readFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { afterEach, test } from 'node:test'
import { escribirEnv, leerEnv, revisarBackend } from './configuracion.mjs'
import { soloDigitos } from './whatsapp.mjs'

const fetchReal = globalThis.fetch
afterEach(() => { globalThis.fetch = fetchReal })

test('el numero se acepta como lo escribe una persona', () => {
  assert.equal(soloDigitos('+57 300 123 4567'), '573001234567')
  assert.equal(soloDigitos('+57-300-123-4567'), '573001234567')
  assert.equal(soloDigitos('3001234'), null)
  assert.equal(soloDigitos('+0573001234567'), null)
  assert.equal(soloDigitos(''), null)
  assert.equal(soloDigitos(undefined), null)
})

test('el .env se escribe y se vuelve a leer igual', () => {
  const ruta = join(mkdtempSync(join(tmpdir(), 'cotizaia-wa-')), '.env')
  const valores = { WHATSAPP_NUMBER: '+573001234567', BACKEND_URL: 'https://x.onrender.com', WHATSAPP_SERVICE_KEY: 'a'.repeat(64) }

  escribirEnv(valores, ruta)

  assert.deepEqual(leerEnv(ruta), valores)
  assert.match(readFileSync(ruta, 'utf8'), /No lo suba a git/)
})

test('sin .env no hay valores', () => {
  assert.deepEqual(leerEnv(join(tmpdir(), 'no-existe-cotizaia', '.env')), {})
})

function responde(status, cuerpo = '') {
  globalThis.fetch = async (url, opciones) => {
    assert.equal(url, 'https://x.onrender.com/api/whatsapp/outbox/0/sent')
    assert.equal(opciones.headers['X-API-Key'], 'clave')
    return { status, text: async () => cuerpo }
  }
}

test('el diagnostico distingue cada problema del backend', async () => {
  const backend = { backendUrl: 'https://x.onrender.com/', apiKey: 'clave' }

  responde(404)
  assert.equal(await revisarBackend(backend), 'ok')
  responde(503)
  assert.equal(await revisarBackend(backend), 'sin-clave')
  responde(401, '{"error":"Invalid service key"}')
  assert.equal(await revisarBackend(backend), 'clave-incorrecta')
  responde(401, '{"error":"Authentication required"}')
  assert.equal(await revisarBackend(backend), 'sin-whatsapp')
  globalThis.fetch = async () => { throw new Error('ENOTFOUND') }
  assert.equal(await revisarBackend(backend), 'sin-conexion')
})
