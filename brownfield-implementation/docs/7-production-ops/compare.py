#!/usr/bin/env python3
"""
Shadow Mode Request-Replay Harness
Sends identical requests to Mule (source) and Spring Boot (target),
compares responses field-by-field, and reports PASS/FAIL.

Usage:
    MULE_BASE_URL=https://mule.example.cloudhub.io/api
    SPRING_BASE_URL=http://k8s-ingress.example.com/api
    YOUTUBE_API_KEY=<key>  # or use --no-auth for unauthenticated test endpoints

    python3 compare.py --mule $MULE_BASE_URL --spring $SPRING_BASE_URL
"""

import argparse
import json
import sys
import time
from typing import Any

try:
    import requests
except ImportError:
    print("ERROR: 'requests' library not installed. Run: pip install requests")
    sys.exit(1)


# ── Request set ──────────────────────────────────────────────────────────────
# Edit these to use real playlist/video IDs from your production environment.

REQUEST_SCENARIOS = [
    {
        "name": "playlist-happy-path",
        "path": "/youtube/playlists/PLtest12345",
        "params": {},
    },
    {
        "name": "playlist-with-page-token",
        "path": "/youtube/playlists/PLtest12345",
        "params": {"pageToken": "EAAelgEKADiD"},
    },
    {
        "name": "video-happy-path",
        "path": "/youtube/song/dQw4w9WgXcQ",
        "params": {},
    },
    {
        "name": "video-not-found",
        "path": "/youtube/song/INVALID_VIDEO_ID",
        "params": {},
        "expect_status_mismatch": True,  # Mule 200, Spring 404 — FR-7 known difference
    },
]

# Fields to EXCLUDE from comparison (known intentional differences)
IGNORED_FIELDS: set[str] = set()


# ── Core comparison logic ─────────────────────────────────────────────────────

def deep_diff(mule: Any, spring: Any, path: str = "$") -> list[dict]:
    """Recursively compare two JSON objects. Returns list of field-level diffs."""
    diffs = []

    if type(mule) != type(spring):
        diffs.append({"path": path, "mule": mule, "spring": spring})
        return diffs

    if isinstance(mule, dict):
        all_keys = set(mule.keys()) | set(spring.keys())
        for key in sorted(all_keys):
            if key in IGNORED_FIELDS:
                continue
            child_path = f"{path}.{key}"
            if key not in mule:
                diffs.append({"path": child_path, "mule": "(missing)", "spring": spring[key]})
            elif key not in spring:
                diffs.append({"path": child_path, "mule": mule[key], "spring": "(missing)"})
            else:
                diffs.extend(deep_diff(mule[key], spring[key], child_path))
    elif isinstance(mule, list):
        if len(mule) != len(spring):
            diffs.append({"path": f"{path}[length]", "mule": len(mule), "spring": len(spring)})
        for i, (m, s) in enumerate(zip(mule, spring)):
            diffs.extend(deep_diff(m, s, f"{path}[{i}]"))
    else:
        if mule != spring:
            diffs.append({"path": path, "mule": mule, "spring": spring})

    return diffs


def call_endpoint(base_url: str, path: str, params: dict) -> tuple[int, Any, float]:
    """Call an endpoint. Returns (status_code, response_body_as_dict, latency_ms)."""
    url = base_url.rstrip("/") + path
    t0 = time.perf_counter()
    resp = requests.get(url, params=params, timeout=10)
    latency_ms = (time.perf_counter() - t0) * 1000.0
    try:
        body = resp.json()
    except Exception:
        body = {"_raw": resp.text}
    return resp.status_code, body, latency_ms


# ── Runner ────────────────────────────────────────────────────────────────────

def run(mule_base: str, spring_base: str) -> bool:
    """Run all scenarios. Returns True if shadow gate PASSES."""
    all_pass = True
    latency_results = []

    print(f"\n{'=' * 70}")
    print(f"Shadow Mode Harness")
    print(f"  Mule   : {mule_base}")
    print(f"  Spring : {spring_base}")
    print(f"{'=' * 70}\n")

    for scenario in REQUEST_SCENARIOS:
        name = scenario["name"]
        path = scenario["path"]
        params = scenario["params"]
        expect_status_mismatch = scenario.get("expect_status_mismatch", False)

        print(f"▶  {name}")
        print(f"   GET {path} params={params or '(none)'}")

        mule_status, mule_body, mule_latency = call_endpoint(mule_base, path, params)
        spring_status, spring_body, spring_latency = call_endpoint(spring_base, path, params)

        latency_results.append({
            "scenario": name,
            "mule_ms": round(mule_latency, 1),
            "spring_ms": round(spring_latency, 1),
            "ratio": round(spring_latency / mule_latency, 2) if mule_latency > 0 else None,
        })

        # Status check
        if mule_status != spring_status:
            if expect_status_mismatch:
                print(f"   ℹ KNOWN STATUS DIFF: Mule={mule_status} Spring={spring_status} (FR-7 breaking change)")
            else:
                print(f"   ✗ STATUS MISMATCH: Mule={mule_status} Spring={spring_status}")
                all_pass = False
            print()
            continue

        # Field-level comparison (only when status matches)
        diffs = deep_diff(mule_body, spring_body)
        if diffs:
            print(f"   ✗ {len(diffs)} FIELD DIFF(S):")
            for d in diffs:
                print(f"     {d['path']}: Mule={d['mule']!r}  Spring={d['spring']!r}")
            all_pass = False
        else:
            print(f"   ✓ PASS  (Mule: {mule_status}/{mule_latency:.0f}ms  Spring: {spring_status}/{spring_latency:.0f}ms)")
        print()

    # Latency summary (NFR-1: ≤20% regression vs Mule baseline)
    print(f"{'─' * 70}")
    print("Latency Summary (NFR-1: Spring must not exceed Mule by >20%)")
    nfr1_pass = True
    for r in latency_results:
        flag = ""
        if r["ratio"] and r["ratio"] > 1.20:
            flag = " ← NFR-1 VIOLATION"
            nfr1_pass = False
            all_pass = False
        print(f"  {r['scenario']:<40} Mule={r['mule_ms']:>6}ms  Spring={r['spring_ms']:>6}ms  ratio={r['ratio']}{flag}")

    print(f"\n{'=' * 70}")
    if all_pass:
        print("SHADOW MODE GATE: PASSED ✓  — Cutover may proceed.")
    else:
        print("SHADOW MODE GATE: FAILED ✗  — Resolve all mismatches before cutover.")
    print(f"{'=' * 70}\n")

    return all_pass


# ── Entry point ───────────────────────────────────────────────────────────────

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Shadow mode request-replay harness")
    parser.add_argument("--mule", required=True, help="Mule CloudHub base URL, e.g. https://app.cloudhub.io/api")
    parser.add_argument("--spring", required=True, help="Spring Boot K8s base URL, e.g. http://svc.cluster.local/api")
    args = parser.parse_args()

    passed = run(args.mule, args.spring)
    sys.exit(0 if passed else 1)
