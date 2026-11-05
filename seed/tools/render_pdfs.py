"""Renders seed/content + seed/manifest.json into PDF proposals (and client briefs) with weasyprint.

Numbers (hours, COP) come only from the manifest; the delegate prose never carries them.
Every page footer marks the document as synthetic sample data.
Run: python3 seed/tools/render_pdfs.py
"""
import datetime
import html
import json
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FOOTER = "DATOS DE MUESTRA SINTÉTICOS - agencia, clientes y cifras ficticios, generados para evaluación"

CSS = """
@page { size: A4; margin: 22mm 18mm 24mm;
  @bottom-center { content: "%s · pág. " counter(page); font: 8pt sans-serif; color: #777; } }
body { font: 10.5pt/1.45 'DejaVu Sans', sans-serif; color: #1d1d1d; }
h1 { font-size: 17pt; margin: 0 0 2mm; } h2 { font-size: 12pt; margin: 7mm 0 2mm; border-bottom: 1px solid #ccc; }
.meta { color: #555; font-size: 9.5pt; } table { width: 100%%; border-collapse: collapse; margin-top: 2mm; }
th, td { border-bottom: 1px solid #ddd; padding: 2mm 1.5mm; vertical-align: top; text-align: left; }
th { background: #f1f1ec; font-size: 9pt; } td.n { text-align: right; white-space: nowrap; }
tr.total td { font-weight: bold; border-top: 2px solid #333; } .brief { white-space: pre-wrap; }
""" % FOOTER


def cop(value):
    return "$" + f"{value:,.0f}".replace(",", ".") + " COP"


def esc(text):
    return html.escape(str(text))


def proposal_html(m, p, c, date):
    rows = []
    for item in p["items"]:
        scope = next(i["scope"] for i in c["items"] if i["itemId"] == item["itemId"])
        rows.append(f"<tr><td><b>{esc(item['requirementType'])}</b><br>{esc(scope)}</td>"
                    f"<td class='n'>{item['approvedHours']} h</td><td class='n'>{cop(item['lineTotalCop'])}</td></tr>")
    rows.append(f"<tr class='total'><td>Total</td><td class='n'>{p['totalHours']} h</td>"
                f"<td class='n'>{cop(p['totalCop'])}</td></tr>")
    bullets = lambda xs: "".join(f"<li>{esc(x)}</li>" for x in xs)
    return f"""<html><head><meta charset='utf-8'><style>{CSS}</style></head><body>
<h1>Propuesta comercial {esc(p['projectId'])}</h1>
<div class='meta'>{esc(m['agency'])} · Cliente: {esc(c['clientName'])} ({esc(p['sector'])}, {esc(p['city'])})
 · Contacto: {esc(c['contactName'])} · Fecha: {date.isoformat()} · Estado: aprobada por el cliente</div>
<h2>Contexto</h2><p>{esc(c['context'])}</p>
<h2>Alcance y esfuerzo aprobado</h2>
<table><tr><th>Entregable</th><th>Horas</th><th>Subtotal</th></tr>{''.join(rows)}</table>
<p class='meta'>Tarifa aplicada: {cop(m['rateCopPerHour'])} por hora.</p>
<h2>Supuestos</h2><ul>{bullets(c['assumptions'])}</ul>
<h2>Exclusiones</h2><ul>{bullets(c['exclusions'])}</ul>
</body></html>"""


def brief_html(p, c, date):
    return f"""<html><head><meta charset='utf-8'><style>{CSS}</style></head><body>
<h1>Solicitud del cliente</h1>
<div class='meta'>{esc(c['clientName'])} · {esc(c['contactName'])} · Canal: {esc(p['channel'])} · {date.isoformat()}</div>
<h2>Mensaje original</h2><p class='brief'>{esc(c['brief'])}</p></body></html>"""


def render(markup, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile("w", suffix=".html", encoding="utf-8", delete=False) as tmp:
        tmp.write(markup)
    subprocess.run(["weasyprint", tmp.name, str(target)], check=True, capture_output=True)
    Path(tmp.name).unlink()


def main():
    m = json.loads((ROOT / "manifest.json").read_text(encoding="utf-8"))
    start = datetime.date(2025, 1, 15)
    count = 0
    for index, p in enumerate(m["projects"]):
        c = json.loads((ROOT / "content" / f"{p['projectId']}.json").read_text(encoding="utf-8"))
        date = start + datetime.timedelta(days=21 * index)
        render(proposal_html(m, p, c, date), ROOT / "pdf" / p["split"] / f"{p['projectId']}-propuesta.pdf")
        render(brief_html(p, c, date), ROOT / "pdf" / p["split"] / f"{p['projectId']}-brief.pdf")
        count += 2
    print(f"rendered {count} PDFs into {ROOT / 'pdf'}")


if __name__ == "__main__":
    main()
