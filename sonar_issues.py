#!/usr/bin/env python3
"""
SonarCloud Issues Fetcher — Fetches ALL open issues via authenticated API.
Usage: py sonar_issues.py [SONAR_TOKEN]
If no token is provided, it will try to read from SONAR_TOKEN env var.
"""
import json
import sys
import urllib.request
import urllib.parse
from collections import defaultdict

# === Configuration ===
ORG = "seifeddine77"
PROJECT_KEY = "autoassist-call-management"
BASE_URL = "https://sonarcloud.io/api/issues/search"

def fetch_issues(token: str, page: int = 1, page_size: int = 100, **extra_params):
    """Fetch a page of issues from SonarCloud API."""
    params = {
        "componentKeys": PROJECT_KEY,
        "organization": ORG,
        "resolved": "false",
        "ps": str(page_size),
        "p": str(page),
        "s": "SEVERITY",
        "asc": "false",
        **extra_params,
    }
    url = f"{BASE_URL}?{urllib.parse.urlencode(params)}"
    req = urllib.request.Request(url)
    # Basic auth: token as username, empty password
    import base64
    credentials = base64.b64encode(f"{token}:".encode()).decode()
    req.add_header("Authorization", f"Basic {credentials}")
    
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read().decode())

def main():
    token = sys.argv[1] if len(sys.argv) > 1 else ""
    if not token:
        import os
        token = os.environ.get("SONAR_TOKEN", "")
    if not token:
        print("ERROR: Provide a SonarCloud token as argument or set SONAR_TOKEN env var")
        print("  Usage: py sonar_issues.py squ_xxxxxxxxxxxxxxxx")
        print("  Get token from: https://sonarcloud.io/account/security")
        sys.exit(1)

    print(f"Fetching issues for {PROJECT_KEY} (org: {ORG})...")
    
    # Fetch all issues (paginated)
    all_issues = []
    page = 1
    while True:
        data = fetch_issues(token, page=page, page_size=100)
        total = data["paging"]["total"]
        all_issues.extend(data["issues"])
        print(f"  Page {page}: {len(data['issues'])} issues (total: {total})")
        if len(all_issues) >= total:
            break
        page += 1

    # === Severity summary ===
    by_severity = defaultdict(int)
    by_file = defaultdict(list)
    for issue in all_issues:
        sev = issue.get("severity", "UNKNOWN")
        by_severity[sev] += 1
        component = issue.get("component", "?")
        # Strip project prefix from component path
        short_path = component.replace(f"{PROJECT_KEY}:", "")
        by_file[short_path].append(issue)

    print(f"\n{'='*60}")
    print(f"TOTAL OPEN ISSUES: {total}")
    print(f"{'='*60}")
    print("\n--- By Severity ---")
    for sev in ["BLOCKER", "CRITICAL", "MAJOR", "MINOR", "INFO"]:
        count = by_severity.get(sev, 0)
        if count > 0:
            emoji = {"BLOCKER": "🔴", "CRITICAL": "🟠", "MAJOR": "🟡", "MINOR": "🔵", "INFO": "⚪"}.get(sev, "")
            print(f"  {emoji} {sev}: {count}")

    # === Issues by file (sorted by count) ===
    print(f"\n--- By File (top 30) ---")
    sorted_files = sorted(by_file.items(), key=lambda x: -len(x[1]))
    for filepath, issues in sorted_files[:30]:
        print(f"\n📁 {filepath} ({len(issues)} issues)")
        for issue in issues[:5]:
            line = issue.get("line", "?")
            msg = issue.get("message", "?")[:100]
            sev = issue.get("severity", "?")
            print(f"   L{line} [{sev}] {msg}")
        if len(issues) > 5:
            print(f"   ... and {len(issues) - 5} more")

    # === Save full JSON for reference ===
    with open("sonar_issues_full.json", "w", encoding="utf-8") as f:
        json.dump(all_issues, f, indent=2, ensure_ascii=False)
    print(f"\nFull issues saved to: sonar_issues_full.json")

if __name__ == "__main__":
    main()
