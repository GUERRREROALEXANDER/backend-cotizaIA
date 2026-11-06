"""Fails when a runtime dependency has a known vulnerability (OSV.dev) that is not explicitly accepted.

Usage (CI and local):
  ./mvnw -B -q dependency:list -DincludeScope=runtime -DoutputFile=target/runtime-deps.txt
  python3 scripts/osv_check.py target/runtime-deps.txt security/osv-allowlist.txt
"""
import datetime
import json
import re
import sys
import urllib.request


def load_dependencies(path):
    queries = []
    for line in open(path, encoding="utf-8"):
        match = re.match(r"\s+([^:\s]+):([^:]+):[^:]+:([^:]+):(compile|runtime)", line)
        if match:
            queries.append({"package": {"ecosystem": "Maven", "name": f"{match.group(1)}:{match.group(2)}"},
                            "version": match.group(3)})
    return queries


def load_allowlist(path):
    """Lines: '<advisory id> <review date YYYY-MM-DD> <reason>'. An expired review date stops being accepted."""
    accepted = {}
    for line in open(path, encoding="utf-8"):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        advisory, review, reason = line.split(" ", 2)
        accepted[advisory] = (datetime.date.fromisoformat(review), reason)
    return accepted


def main():
    queries = load_dependencies(sys.argv[1])
    accepted = load_allowlist(sys.argv[2])
    if not queries:
        print("no runtime dependencies parsed; refusing to pass an empty scan")
        return 2
    request = urllib.request.Request("https://api.osv.dev/v1/querybatch",
                                     data=json.dumps({"queries": queries}).encode(),
                                     headers={"Content-Type": "application/json", "User-Agent": "cotizaia-ci"})
    results = json.load(urllib.request.urlopen(request, timeout=60))["results"]
    today = datetime.date.today()
    failures = []
    for query, result in zip(queries, results):
        for vuln in result.get("vulns", []):
            entry = accepted.get(vuln["id"])
            name = f"{query['package']['name']}:{query['version']}"
            if entry and entry[0] >= today:
                print(f"accepted  {vuln['id']} in {name} until {entry[0]}: {entry[1]}")
            else:
                failures.append(f"{vuln['id']} in {name}" + (" (allowlist review date passed)" if entry else ""))
    print(f"{len(queries)} runtime dependencies scanned, {len(failures)} unaccepted vulnerabilities")
    for failure in failures:
        print(f"VULNERABLE {failure}")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
