"""Carga la agencia de demostración (seed/) en un backend CotizaIA en ejecución, solo a través de la API pública.

Recorre el mismo camino que un usuario real: registro, tarifas, catálogo, clientes, memoria de propuestas
pasadas (24 PDFs), briefs por los cuatro canales (PDF, correo, WhatsApp, formulario web), procesamiento con el
agente y avance de las propuestas por su ciclo de vida (revisión, envío, negociación, aceptación, rechazo).

Uso:
    python scripts/seed_demo.py                       # backend en http://localhost:8080
    python scripts/seed_demo.py --base-url http://localhost:8081

Es idempotente: si la agencia demo ya tiene propuestas, no vuelve a sembrar (usa --force para sembrar otra ronda
de briefs). Solo usa la librería estándar de Python 3.9+.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import time
import unicodedata
import urllib.error
import urllib.request
import uuid
from http.cookies import SimpleCookie
from pathlib import Path
from typing import Any

SEED = Path(__file__).resolve().parent.parent / "seed"

DEMO_AGENCY = "Estudio Nariño Digital"
DEMO_OWNER = "Laura Pantoja"
DEMO_EMAIL = "demo@estudio-narino.test"
DEMO_PASSWORD = "CotizaIA-demo-2026"

# Brief PDF subido por el cliente -> estado final al que se lleva su propuesta.
PDF_BRIEFS = {
    "P07": "ACCEPTED",
    "P12": "NEGOTIATING",
    "P14": "SENT",
    "P24": "REJECTED",
    "P29": "IN_REVIEW",
    "P30": "IN_REVIEW",
}

# Briefs escritos que llegan por correo, WhatsApp o formulario (el canal sale del manifest).
TYPED_BRIEFS = ["P02", "P03", "P05", "P09"]

CHANNEL_FIELD = {"EMAIL": "body", "WHATSAPP": "message", "WEB_FORM": "description"}

ANSWER = ("El cliente confirma el alcance descrito en el brief; contenidos, fotos y accesos los entrega "
          "el cliente y se acepta una ronda de ajustes.")


class ApiError(Exception):
    def __init__(self, status: int, body: str):
        super().__init__(f"HTTP {status}: {body[:300]}")
        self.status = status


class Api:
    def __init__(self, base_url: str):
        self.base = base_url.rstrip("/")
        self.token: str | None = None

    def call(self, method: str, path: str, body=None, *, multipart=None, auth=True) -> tuple[Any, Any]:
        headers = {"Accept": "application/json"}
        data = None
        if multipart is not None:
            data, content_type = encode_multipart(*multipart)
            headers["Content-Type"] = content_type
        elif body is not None:
            data = json.dumps(body).encode("utf-8")
            headers["Content-Type"] = "application/json"
        if auth and self.token:
            headers["Authorization"] = f"Bearer {self.token}"

        for _ in range(8):
            request = urllib.request.Request(self.base + path, data=data, method=method, headers=headers)
            try:
                with urllib.request.urlopen(request, timeout=180) as response:
                    raw = response.read().decode("utf-8")
                    return json.loads(raw) if raw else None, response.headers
            except urllib.error.HTTPError as error:
                text = error.read().decode("utf-8", "replace")
                if error.code == 429:
                    wait = int(error.headers.get("Retry-After", "10"))
                    print(f"    límite de IA alcanzado, esperando {wait}s…")
                    time.sleep(wait + 1)
                    continue
                raise ApiError(error.code, text) from None
        raise ApiError(429, "demasiados reintentos")

    def get(self, path) -> Any:
        return self.call("GET", path)[0]

    def post(self, path, body=None) -> Any:
        return self.call("POST", path, body)[0]


def encode_multipart(fields: dict, files: dict):
    boundary = uuid.uuid4().hex
    parts = []
    for name, value in fields.items():
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'
                     .encode("utf-8"))
    for name, (filename, content, mime) in files.items():
        head = (f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"; filename="{filename}"\r\n'
                f"Content-Type: {mime}\r\n\r\n").encode("utf-8")
        parts.append(head + content + b"\r\n")
    parts.append(f"--{boundary}--\r\n".encode("utf-8"))
    return b"".join(parts), f"multipart/form-data; boundary={boundary}"


def access_token(headers) -> str:
    for header in headers.get_all("Set-Cookie") or []:
        cookie = SimpleCookie()
        cookie.load(header)
        if "cz_access" in cookie:
            return cookie["cz_access"].value
    raise RuntimeError("El backend no devolvió la cookie cz_access")


def slug(text: str, sep: str = ".") -> str:
    plain = unicodedata.normalize("NFKD", text).encode("ascii", "ignore").decode().lower()
    return sep.join(re.findall(r"[a-z0-9]+", plain))


def sign_in(api: Api) -> bool:
    """Devuelve True si la agencia se acaba de crear."""
    credentials = {"email": DEMO_EMAIL, "password": DEMO_PASSWORD}
    try:
        _, headers = api.call("POST", "/api/auth/login", credentials, auth=False)
        api.token = access_token(headers)
        return False
    except ApiError as error:
        if error.status not in (400, 401, 404):
            raise
    _, headers = api.call("POST", "/api/auth/register", {
        "agencyName": DEMO_AGENCY, "ownerFullName": DEMO_OWNER, **credentials}, auth=False)
    api.token = access_token(headers)
    return True


def seed_rates_and_catalog(api: Api, manifest: dict):
    if not api.get("/api/roles"):
        rates = {"Desarrollo web": manifest["rateCopPerHour"], "Diseño y marca": 85000, "Gestión de proyecto": 110000}
        for name, cop in rates.items():
            role = api.post("/api/roles", {"name": name})
            api.post(f"/api/roles/{role['id']}/rates", {"copPerHour": cop})
        print(f"  roles y tarifas: {len(rates)}")

    if not api.get("/api/catalog"):
        catalog = api.post("/api/catalog", {
            "name": "Servicios digitales",
            "description": "Sitios web, comercio electrónico, integraciones y marca para pymes de Nariño."})
        for entry in manifest["catalog"]:
            api.post(f"/api/catalog/{catalog['id']}/requirement-types", {
                "name": entry["name"], "description": entry["keywords"], "estimatedHours": entry["catalogHours"]})
        print(f"  catálogo: {len(manifest['catalog'])} tipos de requerimiento")


def seed_clients(api: Api, contents: dict) -> dict:
    existing = {client["company"]: client["id"] for client in api.get("/api/clients")}
    ids = {}
    for pid, content in contents.items():
        company = content["clientName"]
        if company not in existing:
            client = api.post("/api/clients", {
                "name": content["contactName"], "company": company,
                "email": f"{slug(content['contactName'])}@{slug(company, '-')}.test"})
            existing[company] = client["id"]
        ids[pid] = existing[company]
    print(f"  clientes: {len(ids)}")
    return ids


def seed_memory(api: Api, manifest: dict):
    loaded = {doc["name"] for doc in api.get("/api/knowledge/documents")}
    added = 0
    for project in manifest["projects"]:
        if project["split"] != "train":
            continue
        name = f"{project['projectId']}-propuesta.pdf"
        if name in loaded:
            continue
        pdf = (SEED / "pdf" / "train" / name).read_bytes()
        api.call("POST", "/api/knowledge/documents",
                 multipart=({}, {"file": (name, pdf, "application/pdf")}))
        added += 1
    print(f"  memoria: {added} propuestas pasadas indexadas ({len(loaded)} documentos ya en memoria)")


def process(api: Api, brief_id: int) -> Any:
    result = api.post(f"/api/briefs/{brief_id}/process")
    # El agente se detiene si encuentra ambigüedades bloqueantes: se responden como lo haría la agencia.
    for _ in range(3):
        pending = [q for q in (result.get("questions") or []) if q.get("blocking") and not q.get("answer")]
        if not result.get("halted") or not pending:
            break
        result = api.post(f"/api/briefs/{brief_id}/questions/{pending[0]['id']}/answer", {"answer": ANSWER})
    return result


def advance(api: Api, proposal_id: int, target: str):
    steps = {
        "IN_REVIEW": [],
        "SENT": ["approve", "send"],
        "NEGOTIATING": ["approve", "send", "negotiate"],
        "ACCEPTED": ["approve", "send", "accept"],
        "REJECTED": ["reject"],
    }[target]
    for step in steps:
        try:
            api.post(f"/api/proposals/{proposal_id}/{step}")
        except ApiError as error:
            print(f"    aviso: {step} sobre la propuesta {proposal_id} -> {error}")
            return


def seed_briefs(api: Api, manifest: dict, contents: dict, clients: dict):
    projects = {p["projectId"]: p for p in manifest["projects"]}

    for pid, target in PDF_BRIEFS.items():
        name = f"{pid}-brief.pdf"
        pdf = (SEED / "pdf" / "holdout" / name).read_bytes()
        uploaded, _ = api.call("POST", "/api/briefs/upload", multipart=(
            {"clientId": clients[pid], "note": "Solicitud recibida en PDF"},
            {"file": (name, pdf, "application/pdf")}))
        result = process(api, uploaded["brief"]["id"])
        if result.get("proposalId"):
            advance(api, result["proposalId"], target)
            result["proposalStatus"] = api.get(f"/api/proposals/{result['proposalId']}")["status"]
        report(pid, "PDF", uploaded["pages"], result)

    for pid in TYPED_BRIEFS:
        channel = projects[pid]["channel"]
        brief = api.post("/api/briefs", {
            "source": channel, "clientId": clients[pid],
            "payload": {CHANNEL_FIELD[channel]: contents[pid]["brief"]}})
        result = process(api, brief["id"])
        report(pid, channel, None, result)


def report(pid, channel, pages, result):
    total = result.get("total")
    money = f"${float(total):,.0f} COP".replace(",", ".") if total is not None else "sin total"
    extra = f", {pages} pág." if pages else ""
    print(f"  brief {pid} ({channel}{extra}) -> propuesta {result.get('proposalId')} "
          f"{result.get('proposalStatus')} {money}")


def main():
    parser = argparse.ArgumentParser(description="Carga la agencia de demostración de seed/ vía la API.")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--force", action="store_true", help="siembra otra ronda de briefs aunque ya existan")
    args = parser.parse_args()

    manifest = json.loads((SEED / "manifest.json").read_text(encoding="utf-8"))
    contents = {p["projectId"]: json.loads((SEED / "content" / f"{p['projectId']}.json").read_text(encoding="utf-8"))
                for p in manifest["projects"]}
    api = Api(args.base_url)

    try:
        created = sign_in(api)
    except (urllib.error.URLError, ConnectionError) as error:
        sys.exit(f"No se pudo conectar con {args.base_url}: {error}. ¿Está corriendo el backend?")
    print(f"Agencia '{DEMO_AGENCY}' {'creada' if created else 'encontrada'} ({DEMO_EMAIL})")

    seed_rates_and_catalog(api, manifest)
    clients = seed_clients(api, contents)
    seed_memory(api, manifest)

    if api.get("/api/proposals") and not args.force:
        print("  la agencia ya tiene propuestas: no se siembran briefs (usa --force para otra ronda)")
    else:
        seed_briefs(api, manifest, contents, clients)

    analytics = api.get("/api/analytics")
    print("\nListo. Inicia sesión en el frontend con:")
    print(f"  correo:     {DEMO_EMAIL}\n  contraseña: {DEMO_PASSWORD}")
    print(f"Resumen: {json.dumps(analytics, ensure_ascii=False)[:400]}")


if __name__ == "__main__":
    main()
