#!/usr/bin/env python3
"""Fetch public source snapshots for :app:auditSources. No accounts or cookies."""
import argparse
import concurrent.futures
import json
import pathlib
import urllib.request


def fetch(url):
    request = urllib.request.Request(url, headers={"User-Agent": "DDRChartViewer-source-audit/1.0"})
    with urllib.request.urlopen(request, timeout=45) as response:
        return response.read()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, type=pathlib.Path)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    versions = json.loads(fetch("https://assets.bjmania.com/mdb/ver.json"))
    version = max(map(int, versions["MDX"]["mdb"]))
    files = {
        "songdata.js": "https://3icecream.com/js/songdata.js",
        "MDX_mdb.bin": f"https://assets.bjmania.com/mdb/MDX_mdb_{version}.bin",
    }
    for mode, spdp in (("SP", 0), ("DP", 1)):
        for level in range(1, 20):
            files[f"difficulty-{mode}-{level}.html"] = f"https://3icecream.com/difficulty_list/{level}?spdp={spdp}"
    # Constant BPM, stops, dense BPM changes, and Shock arrows; SP and DP.
    for song in (
        "00qIiO99Q18biD8bQ6d1QOooDP00O1Od", "00obPO6oPIPOoD9qb0dIl6q6D8P6o9bI",
        "QQldo10ObPPQPlliODiDIIl0Q1oPoo61", "qQ9Oo611P0dObD8q6O0Q968bbl8I91OO",
    ):
        files[f"chart-{song}.json"] = f"https://3icecream.com/api/chart_info/{song}"

    def save(item):
        name, url = item
        (args.output / name).write_bytes(fetch(url))
        return name

    with concurrent.futures.ThreadPoolExecutor(max_workers=3) as executor:
        for name in executor.map(save, files.items()):
            print(name, flush=True)
    (args.output / "sources.json").write_text(json.dumps(files, indent=2))


if __name__ == "__main__":
    main()
