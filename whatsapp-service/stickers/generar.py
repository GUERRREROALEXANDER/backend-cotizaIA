"""Dibuja los stickers del robot de CotizaIA (512x512 WebP con fondo transparente, como pide WhatsApp).

Arte propio hecho con figuras de Pillow, en los colores de la marca. Uso:  python stickers/generar.py
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

LIMA = (159, 232, 112, 255)
LIMA_OSCURO = (122, 196, 80, 255)
BOSQUE = (22, 51, 0, 255)
BLANCO = (255, 255, 255, 255)
CREMA = (250, 252, 245, 255)
ROSA = (255, 160, 150, 255)
AMARILLO = (255, 214, 82, 255)

ESCALA = 4  # se dibuja en grande y se reduce: bordes suaves
LADO = 512 * ESCALA
CARPETA = Path(__file__).resolve().parent


def s(*valores):
    return [v * ESCALA for v in valores]


def fuente(tamano):
    for nombre in ("seguibl.ttf", "segoeuib.ttf", "arialbd.ttf", "DejaVuSans-Bold.ttf"):
        try:
            return ImageFont.truetype(nombre, tamano * ESCALA)
        except OSError:
            continue
    return ImageFont.load_default()


def linea(d, puntos, ancho=10):
    d.line(s(*puntos), fill=BOSQUE, width=ancho * ESCALA, joint="curve")
    for x, y in zip(puntos[::2], puntos[1::2]):
        r = ancho / 2
        d.ellipse(s(x - r, y - r, x + r, y + r), fill=BOSQUE)


def brazo(d, hombro, mano, color=LIMA):
    linea(d, [*hombro, *mano], 26)
    d.line(s(*hombro, *mano), fill=color, width=12 * ESCALA)
    x, y = mano
    d.ellipse(s(x - 22, y - 22, x + 22, y + 22), fill=color, outline=BOSQUE, width=8 * ESCALA)


def robot(d, ojos="normal", boca="sonrisa"):
    # antena
    linea(d, [256, 112, 256, 70], 10)
    d.ellipse(s(234, 40, 278, 84), fill=AMARILLO, outline=BOSQUE, width=8 * ESCALA)
    # cuerpo
    d.rounded_rectangle(s(176, 318, 336, 444), radius=34 * ESCALA, fill=LIMA, outline=BOSQUE, width=9 * ESCALA)
    d.rounded_rectangle(s(214, 350, 298, 410), radius=14 * ESCALA, fill=CREMA, outline=BOSQUE, width=6 * ESCALA)
    d.ellipse(s(244, 368, 268, 392), fill=LIMA_OSCURO)
    # cabeza
    d.rounded_rectangle(s(136, 108, 376, 320), radius=64 * ESCALA, fill=LIMA, outline=BOSQUE, width=10 * ESCALA)
    d.rounded_rectangle(s(164, 140, 348, 292), radius=46 * ESCALA, fill=CREMA, outline=BOSQUE, width=7 * ESCALA)
    # orejas
    for x in (118, 362):
        d.rounded_rectangle(s(x, 186, x + 32, 246), radius=12 * ESCALA, fill=LIMA_OSCURO, outline=BOSQUE,
                            width=8 * ESCALA)
    # mejillas
    for x in (190, 298):
        d.ellipse(s(x - 18, 238, x + 18, 258), fill=ROSA)
    # ojos
    for x in (212, 300):
        if ojos == "feliz":
            d.arc(s(x - 24, 186, x + 24, 230), start=200, end=340, fill=BOSQUE, width=10 * ESCALA)
        elif ojos == "arriba":
            d.ellipse(s(x - 22, 176, x + 22, 226), fill=BOSQUE)
            d.ellipse(s(x - 6, 180, x + 12, 198), fill=BLANCO)
        elif ojos == "guino" and x == 300:
            d.arc(s(x - 24, 186, x + 24, 230), start=200, end=340, fill=BOSQUE, width=10 * ESCALA)
        else:
            d.ellipse(s(x - 22, 180, x + 22, 230), fill=BOSQUE)
            d.ellipse(s(x - 2, 188, x + 14, 204), fill=BLANCO)
    # boca
    if boca == "sonrisa":
        d.arc(s(226, 226, 286, 272), start=20, end=160, fill=BOSQUE, width=9 * ESCALA)
    elif boca == "abierta":
        d.chord(s(222, 236, 290, 284), start=0, end=180, fill=BOSQUE)
        d.chord(s(240, 262, 272, 284), start=200, end=340, fill=ROSA)
    elif boca == "o":
        d.ellipse(s(244, 248, 268, 274), fill=BOSQUE)


def burbuja(d, caja, texto, tamano, cola):
    x0, y0, x1, y1 = caja
    d.polygon(s(*cola), fill=BLANCO, outline=BOSQUE)
    d.rounded_rectangle(s(*caja), radius=28 * ESCALA, fill=BLANCO, outline=BOSQUE, width=8 * ESCALA)
    d.polygon(s(*[c + (2 if i % 2 else 0) for i, c in enumerate(cola)]), fill=BLANCO)
    f = fuente(tamano)
    ancho = d.textlength(texto, font=f)
    d.text(((x0 + x1) / 2 * ESCALA - ancho / 2, (y0 + y1) / 2 * ESCALA), texto, font=f, fill=BOSQUE, anchor="lm")


def saludo(d):
    brazo(d, (188, 350), (120, 312))
    brazo(d, (324, 346), (402, 250))
    for x0, y0, x1, y1 in [(424, 214, 444, 196), (432, 246, 458, 242), (424, 280, 446, 292)]:
        linea(d, [x0, y0, x1, y1], 8)
    robot(d, ojos="feliz", boca="abierta")
    burbuja(d, (20, 24, 214, 104), "¡Hola!", 46, (150, 100, 196, 100, 176, 132))


def pensando(d):
    brazo(d, (188, 350), (130, 400))
    brazo(d, (324, 350), (300, 300))
    robot(d, ojos="arriba", boca="o")
    for cx, cy, r in [(392, 108, 12), (420, 76, 18)]:
        d.ellipse(s(cx - r, cy - r, cx + r, cy + r), fill=BLANCO, outline=BOSQUE, width=6 * ESCALA)
    d.ellipse(s(392, 6, 500, 72), fill=BLANCO, outline=BOSQUE, width=8 * ESCALA)
    f = fuente(40)
    d.text((446 * ESCALA, 40 * ESCALA), "?", font=f, fill=BOSQUE, anchor="mm")


def listo(d):
    brazo(d, (188, 350), (124, 396))
    brazo(d, (324, 346), (404, 270))
    # pulgar arriba
    linea(d, [404, 250, 404, 218], 22)
    d.line(s(404, 250, 404, 218), fill=LIMA, width=8 * ESCALA)
    robot(d, ojos="guino", boca="sonrisa")
    d.ellipse(s(24, 30, 150, 156), fill=LIMA, outline=BOSQUE, width=10 * ESCALA)
    linea(d, [56, 94, 80, 120, 122, 66], 18)
    burbuja(d, (300, 12, 500, 88), "¡Listo!", 42, (360, 84, 400, 84, 360, 114))


def cotizar(d):
    # hoja de cotizacion que sostiene con ambas manos
    d.rounded_rectangle(s(330, 220, 470, 400), radius=14 * ESCALA, fill=BLANCO, outline=BOSQUE, width=8 * ESCALA)
    f = fuente(54)
    d.text((400 * ESCALA, 270 * ESCALA), "$", font=f, fill=BOSQUE, anchor="mm")
    for y in (320, 346, 372):
        linea(d, [352, y, 448, y], 8)
    brazo(d, (188, 350), (126, 398))
    brazo(d, (324, 350), (346, 330))
    robot(d, ojos="normal", boca="sonrisa")
    d.ellipse(s(30, 40, 92, 102), fill=AMARILLO, outline=BOSQUE, width=7 * ESCALA)
    d.text((61 * ESCALA, 70 * ESCALA), "+", font=fuente(48), fill=BOSQUE, anchor="mm")


def sticker(nombre, dibujar):
    capa = Image.new("RGBA", (LADO, LADO), (0, 0, 0, 0))
    dibujar(ImageDraw.Draw(capa))
    # borde blanco de sticker alrededor de toda la figura
    alfa = capa.getchannel("A").point([0] * 9 + [255] * 247)
    contorno = alfa.filter(ImageFilter.MaxFilter(13 * ESCALA + 1 if (13 * ESCALA) % 2 == 0 else 13 * ESCALA))
    fondo = Image.new("RGBA", (LADO, LADO), (0, 0, 0, 0))
    fondo.paste(BLANCO, mask=contorno)
    fondo.alpha_composite(capa)
    final = fondo.resize((512, 512), Image.Resampling.LANCZOS)
    destino = CARPETA / f"{nombre}.webp"
    final.save(destino, "WEBP", quality=88, method=6)
    return destino


if __name__ == "__main__":
    for nombre, dibujar in [("saludo", saludo), ("pensando", pensando), ("listo", listo), ("cotizar", cotizar)]:
        ruta = sticker(nombre, dibujar)
        print(f"{ruta.name}: {ruta.stat().st_size // 1024} KB")
