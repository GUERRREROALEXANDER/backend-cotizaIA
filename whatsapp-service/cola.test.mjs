// Pruebas de la cola sin WhatsApp ni backend reales: fetch se reemplaza por uno falso.
// Uso: npm test
import assert from 'node:assert/strict'
import { afterEach, test } from 'node:test'
import { NumeroSinWhatsapp, pedirRespuesta, procesarCola, telefonoDe, textoEntrante } from './cola.mjs'

const fetchReal = globalThis.fetch
afterEach(() => { globalThis.fetch = fetchReal })

function backendFalso(items) {
  const llamadas = []
  globalThis.fetch = async (url, opciones) => {
    llamadas.push({ url, headers: opciones.headers, body: opciones.body && JSON.parse(opciones.body) })
    const json = url.endsWith('/claim') ? { items } : null
    return { ok: true, status: url.endsWith('/claim') ? 200 : 204, json: async () => json }
  }
  return llamadas
}

const comun = { backendUrl: 'http://localhost:8080/', apiKey: 'clave', pausaMs: 0 }

test('envia cada mensaje y lo reporta como enviado', async () => {
  const llamadas = backendFalso([{ id: 1, phone: '+573001234567', body: 'Tu codigo es 123456' }])
  const enviados = []

  const total = await procesarCola({ ...comun, enviar: async (telefono, texto) => enviados.push([telefono, texto]) })

  assert.equal(total, 1)
  assert.deepEqual(enviados, [['+573001234567', 'Tu codigo es 123456']])
  assert.deepEqual(llamadas.map((llamada) => llamada.url), [
    'http://localhost:8080/api/whatsapp/outbox/claim',
    'http://localhost:8080/api/whatsapp/outbox/1/sent',
  ])
  assert.equal(llamadas[0].headers['X-API-Key'], 'clave')
})

test('un numero sin WhatsApp se reporta como fallido', async () => {
  const llamadas = backendFalso([{ id: 2, phone: '+573000000000', body: 'hola' }])

  const total = await procesarCola({ ...comun, enviar: async () => { throw new NumeroSinWhatsapp('573000000000') } })

  assert.equal(total, 0)
  assert.equal(llamadas[1].url, 'http://localhost:8080/api/whatsapp/outbox/2/failed')
  assert.deepEqual(llamadas[1].body, { error: 'number is not on WhatsApp' })
})

test('un fallo pasajero no se reporta: el backend lo vuelve a ofrecer', async () => {
  const llamadas = backendFalso([{ id: 3, phone: '+573001234567', body: 'hola' }])
  const errorOriginal = console.error
  console.error = () => {}
  try {
    const total = await procesarCola({ ...comun, enviar: async () => { throw new Error('sin conexion') } })
    assert.equal(total, 0)
  } finally {
    console.error = errorOriginal
  }
  assert.equal(llamadas.length, 1)
})

test('una respuesta de error del backend detiene la revision', async () => {
  globalThis.fetch = async () => ({ ok: false, status: 401 })

  await assert.rejects(procesarCola({ ...comun, enviar: async () => {} }), /401/)
})

test('el bot pide la respuesta al backend con el remitente y el texto', async () => {
  const llamadas = []
  globalThis.fetch = async (url, opciones) => {
    llamadas.push({ url, body: JSON.parse(opciones.body) })
    return { ok: true, status: 200, json: async () => ({ reply: 'Entre $3.000.000 y $4.000.000' }) }
  }

  const respuesta = await pedirRespuesta({ ...comun, de: '573001234567@s.whatsapp.net', texto: 'pagina web' })

  assert.equal(respuesta, 'Entre $3.000.000 y $4.000.000')
  assert.equal(llamadas[0].url, 'http://localhost:8080/api/whatsapp/bot/reply')
  assert.deepEqual(llamadas[0].body, { from: '573001234567@s.whatsapp.net', text: 'pagina web' })
})

test('sin respuesta del backend el bot no contesta', async () => {
  globalThis.fetch = async () => ({ ok: true, status: 200, json: async () => ({ reply: null }) })

  assert.equal(await pedirRespuesta({ ...comun, de: 'x', texto: 'hola' }), null)
})

test('solo se contestan mensajes de texto de personas', () => {
  const de = (remoteJid, message, fromMe = false) => ({ key: { remoteJid, fromMe }, message })

  assert.equal(textoEntrante(de('57300@s.whatsapp.net', { conversation: ' hola ' })), 'hola')
  assert.equal(textoEntrante(de('57300@s.whatsapp.net', { extendedTextMessage: { text: 'web' } })), 'web')
  assert.equal(textoEntrante(de('1234@lid', { conversation: 'hola' })), 'hola')
  assert.equal(textoEntrante(de('57300@s.whatsapp.net', { conversation: 'hola' }, true)), null)
  assert.equal(textoEntrante(de('123-456@g.us', { conversation: 'hola' })), null)
  assert.equal(textoEntrante(de('status@broadcast', { conversation: 'hola' })), null)
  assert.equal(textoEntrante(de('57300@s.whatsapp.net', { imageMessage: {} })), null)
  assert.equal(textoEntrante(de('57300@s.whatsapp.net', { conversation: '   ' })), null)
})
test('el numero real se envia al backend para reconocer usuarios verificados', async () => {
  const llamadas = []
  globalThis.fetch = async (url, opciones) => {
    llamadas.push(JSON.parse(opciones.body))
    return { ok: true, status: 200, json: async () => ({ reply: 'ok' }) }
  }

  await pedirRespuesta({ ...comun, de: '1234@lid', telefono: '573010944197', texto: 'cotizar' })
  await pedirRespuesta({ ...comun, de: '1234@lid', telefono: null, texto: 'hola' })

  assert.deepEqual(llamadas[0], { from: '1234@lid', text: 'cotizar', phone: '573010944197' })
  assert.deepEqual(llamadas[1], { from: '1234@lid', text: 'hola' })
})

test('el numero sale del chat o de su alias cuando WhatsApp usa @lid', () => {
  const de = (key) => ({ key })

  assert.equal(telefonoDe(de({ remoteJid: '573010944197@s.whatsapp.net' })), '573010944197')
  assert.equal(telefonoDe(de({ remoteJid: '573010944197:12@s.whatsapp.net' })), '573010944197')
  assert.equal(telefonoDe(de({ remoteJid: '98765@lid', remoteJidAlt: '573010944197@s.whatsapp.net' })), '573010944197')
  assert.equal(telefonoDe(de({ remoteJid: '98765@lid', senderPn: '573010944197@s.whatsapp.net' })), '573010944197')
  assert.equal(telefonoDe(de({ remoteJid: '98765@lid' })), null)
  assert.equal(telefonoDe(de({ remoteJid: '123-456@g.us' })), null)
  assert.equal(telefonoDe({}), null)
})