"""Generates the synthetic ground truth for the estimation-memory eval.

Each past project is a set of line items. Every item has a catalog requirement type and
"drivers" (page count, product count, number of payment gateways...). The real approved
hours follow hidden rules over those drivers plus noise, so the agency's fixed catalog
hours are systematically wrong and only a method that reads the drivers from similar past
projects can beat them. The rules live only here; the documents state the drivers in
prose and never the formula.

Run: python3 seed/tools/gen_manifest.py  (deterministic, seed fixed below)
"""
import json
import random
from pathlib import Path

SEED = 20261105
RATE_COP_PER_HOUR = 95_000
AGENCY = "Estudio Nariño Digital (agencia ficticia)"

# name, catalog keywords (what the agency typed into its catalog), fixed catalog hours
CATALOG = {
    "Sitio web informativo": ("pagina web, sitio, paginas, landing, informativo", 30),
    "Tienda en linea": ("tienda, ecommerce, productos, carrito, catalogo", 60),
    "Sistema de reservas": ("reserva, reservas, agenda, citas, turnos", 32),
    "Pasarela de pagos": ("pagos, pasarela, PSE, tarjeta, Wompi, PayU", 16),
    "Blog y gestor de contenidos": ("blog, noticias, articulos, gestor de contenidos", 12),
    "Integracion WhatsApp": ("whatsapp, chat, mensajes, notificaciones", 10),
    "Sitio multilenguaje": ("idiomas, ingles, traduccion, multilenguaje", 12),
    "Identidad de marca": ("logo, marca, branding, manual de marca, papeleria", 40),
    "Panel administrativo": ("panel, administracion, dashboard, inventario, reportes", 40),
    "SEO inicial": ("seo, google, posicionamiento, buscadores", 8),
}


def drivers_for(kind, rng):
    if kind == "Sitio web informativo":
        return {"paginas": rng.randint(3, 12), "diseno_a_medida": rng.random() < 0.5}
    if kind == "Tienda en linea":
        return {"productos": rng.choice([20, 40, 80, 150, 300, 450, 600]), "migracion_desde_excel": rng.random() < 0.4}
    if kind == "Sistema de reservas":
        return {"sedes": rng.randint(1, 4), "sincroniza_google_calendar": rng.random() < 0.4}
    if kind == "Pasarela de pagos":
        return {"pasarelas": rng.sample(["PSE", "Wompi", "PayU", "Nequi"], rng.randint(1, 3))}
    if kind == "Blog y gestor de contenidos":
        return {"roles_de_editor": rng.randint(1, 4)}
    if kind == "Integracion WhatsApp":
        return {"tipo": rng.choice(["boton de chat", "bot con notificaciones automaticas"])}
    if kind == "Sitio multilenguaje":
        return {"idiomas": rng.randint(2, 4)}
    if kind == "Identidad de marca":
        return {"alcance": rng.choice(["solo logo", "logo y manual de marca", "manual completo con papeleria"])}
    if kind == "Panel administrativo":
        return {"modulos": rng.randint(2, 8)}
    if kind == "SEO inicial":
        return {"paginas_a_optimizar": rng.randint(3, 15)}
    raise ValueError(kind)


def true_hours(kind, d):
    """Hidden rules. The fixed catalog value never looks at the drivers."""
    if kind == "Sitio web informativo":
        return (10 + 5 * d["paginas"]) * (1.3 if d["diseno_a_medida"] else 1.0)
    if kind == "Tienda en linea":
        return 35 + 0.08 * d["productos"] + (15 if d["migracion_desde_excel"] else 0)
    if kind == "Sistema de reservas":
        return 20 + 8 * d["sedes"] + (10 if d["sincroniza_google_calendar"] else 0)
    if kind == "Pasarela de pagos":
        return 12 * len(d["pasarelas"])
    if kind == "Blog y gestor de contenidos":
        return 10 + 2.5 * d["roles_de_editor"]
    if kind == "Integracion WhatsApp":
        return 6 if d["tipo"] == "boton de chat" else 28
    if kind == "Sitio multilenguaje":
        return 8 * (d["idiomas"] - 1)
    if kind == "Identidad de marca":
        return {"solo logo": 25, "logo y manual de marca": 45, "manual completo con papeleria": 70}[d["alcance"]]
    if kind == "Panel administrativo":
        return 20 + 6 * d["modulos"]
    if kind == "SEO inicial":
        return 6 + 1 * d["paginas_a_optimizar"]
    raise ValueError(kind)


SECTORS = ["restaurante", "clinica odontologica", "ferreteria", "hotel boutique", "colegio privado",
           "tienda de ropa", "veterinaria", "gimnasio", "estudio de abogados", "cafeteria de especialidad",
           "taller de motos", "inmobiliaria", "floristeria", "academia de ingles", "panaderia",
           "spa", "agencia de viajes", "optica", "constructora", "libreria"]
CITIES = ["Pasto", "Ipiales", "Popayan", "Cali", "Tuquerres", "Tumaco"]
CHANNELS = ["WHATSAPP", "EMAIL", "WEB_FORM"]

# Typical bundles per sector keep projects realistic; extra items are drawn at random.
BUNDLES = [
    ["Sitio web informativo", "Sistema de reservas"],
    ["Tienda en linea", "Pasarela de pagos"],
    ["Sitio web informativo", "Blog y gestor de contenidos"],
    ["Identidad de marca", "Sitio web informativo"],
    ["Tienda en linea", "Panel administrativo"],
    ["Sitio web informativo", "Integracion WhatsApp"],
]


def main():
    rng = random.Random(SEED)
    projects = []
    for number in range(1, 31):
        kinds = list(rng.choice(BUNDLES))
        for extra in rng.sample(list(CATALOG), rng.randint(0, 2)):
            if extra not in kinds:
                kinds.append(extra)
        items = []
        for index, kind in enumerate(kinds, start=1):
            d = drivers_for(kind, rng)
            noisy = true_hours(kind, d) * rng.gauss(1.0, 0.07)
            hours = max(2, round(noisy))
            items.append({
                "itemId": f"P{number:02d}-I{index}",
                "requirementType": kind,
                "drivers": d,
                "catalogHours": CATALOG[kind][1],
                "approvedHours": hours,
                "lineTotalCop": hours * RATE_COP_PER_HOUR,
            })
        projects.append({
            "projectId": f"P{number:02d}",
            "sector": rng.choice(SECTORS),
            "city": rng.choice(CITIES),
            "channel": rng.choice(CHANNELS),
            "items": items,
            "totalHours": sum(i["approvedHours"] for i in items),
            "totalCop": sum(i["lineTotalCop"] for i in items),
        })

    # Holdout: 6 projects whose every requirement type appears at least 3 times in train.
    order = list(range(30))
    rng.shuffle(order)
    holdout = []
    for candidate in order:
        trial = holdout + [candidate]
        train = [p for i, p in enumerate(projects) if i not in trial]
        counts = {}
        for p in train:
            for it in p["items"]:
                counts[it["requirementType"]] = counts.get(it["requirementType"], 0) + 1
        if all(counts.get(it["requirementType"], 0) >= 3 for p_i in trial for it in projects[p_i]["items"]):
            holdout = trial
        if len(holdout) == 6:
            break
    for i, p in enumerate(projects):
        p["split"] = "holdout" if i in holdout else "train"

    out = {
        "notice": "SYNTHETIC SAMPLE DATA - fictional agency, clients and numbers, generated for evaluation",
        "seed": SEED,
        "agency": AGENCY,
        "rateCopPerHour": RATE_COP_PER_HOUR,
        "catalog": [{"name": k, "keywords": v[0], "catalogHours": v[1]} for k, v in CATALOG.items()],
        "projects": projects,
    }
    path = Path(__file__).resolve().parent.parent / "manifest.json"
    path.write_text(json.dumps(out, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    # Baseline the eval must beat: MAE of the fixed catalog value on the holdout items.
    hold = [it for p in projects if p["split"] == "holdout" for it in p["items"]]
    mae = sum(abs(it["catalogHours"] - it["approvedHours"]) for it in hold) / len(hold)
    print(f"wrote {path} | holdout items={len(hold)} | catalog MAE={mae:.2f} h | target <= {0.7 * mae:.2f} h")


if __name__ == "__main__":
    main()
