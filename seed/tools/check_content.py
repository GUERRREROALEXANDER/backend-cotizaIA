"""Judge for the delegate-written prose in seed/content/PNN.json.

Passes only if every file matches the manifest item ids, states every driver so a reader
(or a retriever) can recover it, and leaks no hours, prices or currency.
Run: python3 tools/check_content.py [P01 P02 ...]   (no args = all 30)
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
FORBIDDEN = re.compile(r"\bhoras?\b|\$|\bCOP\b|\bpesos\b|\bprecio|\bvalor\b|\bcosto|\btarifa", re.I)


def norm(text):
    table = str.maketrans("áéíóúÁÉÍÓÚñÑ", "aeiouAEIOUnN")
    return text.translate(table).lower()


def driver_errors(kind, d, text):
    t = norm(text)
    errs = []
    def need(pattern, label):
        if not re.search(pattern, t):
            errs.append(f"{kind}: missing driver {label}")
    if kind == "Sitio web informativo":
        need(rf"\b{d['paginas']}\s+(paginas|secciones)", f"{d['paginas']} paginas")
        need(r"a medida|personalizad" if d["diseno_a_medida"] else r"plantilla", "diseno_a_medida")
    elif kind == "Tienda en linea":
        need(rf"\b{d['productos']}\s+(productos|referencias)", f"{d['productos']} productos")
        if d["migracion_desde_excel"]:
            need(r"excel", "migracion_desde_excel")
    elif kind == "Sistema de reservas":
        need(rf"\b{d['sedes']}\s+sedes?\b", f"{d['sedes']} sedes")
        if d["sincroniza_google_calendar"]:
            need(r"google calendar", "google calendar")
    elif kind == "Pasarela de pagos":
        for name in d["pasarelas"]:
            need(re.escape(norm(name)), name)
    elif kind == "Blog y gestor de contenidos":
        need(rf"\b{d['roles_de_editor']}\s+(roles?|editores|perfiles)", f"{d['roles_de_editor']} roles")
    elif kind == "Integracion WhatsApp":
        need(r"\bbot\b|automatic" if d["tipo"].startswith("bot") else r"boton", d["tipo"])
    elif kind == "Sitio multilenguaje":
        need(rf"\b{d['idiomas']}\s+idiomas", f"{d['idiomas']} idiomas")
    elif kind == "Identidad de marca":
        pattern = {"solo logo": r"logo", "logo y manual de marca": r"manual de marca",
                   "manual completo con papeleria": r"papeleria"}[d["alcance"]]
        need(pattern, d["alcance"])
    elif kind == "Panel administrativo":
        need(rf"\b{d['modulos']}\s+modulos", f"{d['modulos']} modulos")
    elif kind == "SEO inicial":
        need(rf"\b{d['paginas_a_optimizar']}\s+paginas", f"{d['paginas_a_optimizar']} paginas")
    return errs


def check(project):
    pid = project["projectId"]
    path = ROOT / "content" / f"{pid}.json"
    if not path.exists():
        return [f"{pid}: file missing"]
    try:
        c = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as e:
        return [f"{pid}: invalid JSON {e}"]
    errs = []
    for key in ("projectId", "clientName", "contactName", "brief", "context", "items", "assumptions", "exclusions"):
        if key not in c:
            errs.append(f"{pid}: missing key {key}")
    if errs:
        return errs
    if not 350 <= len(c["brief"]) <= 1200:
        errs.append(f"{pid}: brief length {len(c['brief'])} outside 350-1200")
    ids = [i.get("itemId") for i in c["items"]]
    want = [i["itemId"] for i in project["items"]]
    if ids != want:
        errs.append(f"{pid}: item ids {ids} != {want}")
    texts = [c["brief"], c["context"], *c["assumptions"], *c["exclusions"], *(i.get("scope", "") for i in c["items"])]
    for t in texts:
        m = FORBIDDEN.search(t)
        if m:
            errs.append(f"{pid}: forbidden word '{m.group(0)}' in: {t[:60]}...")
    scopes = {i.get("itemId"): i.get("scope", "") for i in c["items"]}
    for item in project["items"]:
        kind, d = item["requirementType"], item["drivers"]
        errs += [f"{pid} brief: {e}" for e in driver_errors(kind, d, c["brief"])]
        errs += [f"{pid} {item['itemId']} scope: {e}" for e in driver_errors(kind, d, scopes.get(item["itemId"], ""))]
    if not (2 <= len(c["assumptions"]) <= 5 and 2 <= len(c["exclusions"]) <= 5):
        errs.append(f"{pid}: need 2-5 assumptions and 2-5 exclusions")
    return errs


def main():
    manifest = json.loads((ROOT / "manifest.json").read_text(encoding="utf-8"))
    wanted = set(sys.argv[1:])
    projects = [p for p in manifest["projects"] if not wanted or p["projectId"] in wanted]
    failures = [e for p in projects for e in check(p)]
    for e in failures:
        print("FAIL", e)
    ok = len(projects) - len({e.split(":")[0].split()[0] for e in failures})
    print(f"{ok}/{len(projects)} projects pass")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
