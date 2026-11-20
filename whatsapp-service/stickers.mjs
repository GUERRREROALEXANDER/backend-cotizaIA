// Los stickers del robot de CotizaIA (carpeta stickers/, generados con stickers/generar.py).
// El backend solo dice el nombre; aqui se valida contra una lista fija, asi nunca se lee otro archivo.
import { readFile } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'

export const STICKERS = ['saludo', 'pensando', 'listo', 'cotizar']
const cache = new Map()

// El contenido WebP del sticker, o null si el nombre no es uno de los conocidos o el archivo falta.
export async function sticker(nombre) {
  if (!STICKERS.includes(nombre)) return null
  if (!cache.has(nombre)) {
    const ruta = fileURLToPath(new URL(`./stickers/${nombre}.webp`, import.meta.url))
    cache.set(nombre, await readFile(ruta).catch(() => null))
  }
  return cache.get(nombre)
}
