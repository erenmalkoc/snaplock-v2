#!/usr/bin/env python3
"""Refreshes app/src/main/assets/tfa-totp-domains.txt from the 2FA Directory.

Run it from anywhere before a release: python3 -I scripts/update-tfa-domains.py

The list says which websites offer authenticator app (TOTP) codes. Snaplock uses it
offline, in the password health check, to point out logins that could have 2FA but have
no one-time code yet. Nothing is looked up at runtime.

Data: https://api.2fa.directory/v3/totp.json, MIT license,
Copyright (c) 2021 2factorauth and Contributors, https://github.com/2factorauth/twofactorauth
"""
import datetime
import json
import pathlib
import re
import urllib.request

URL = "https://api.2fa.directory/v3/totp.json"
OUT = pathlib.Path(__file__).resolve().parent.parent / "app/src/main/assets/tfa-totp-domains.txt"
DOMAIN = re.compile(r"^(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z]{2,}$")


def clean(value):
    host = value.strip().lower()
    host = host.split("://", 1)[-1].split("/", 1)[0].split(":", 1)[0].strip(".")
    return host if DOMAIN.match(host) else None


def main():
    # The API sits behind a filter that refuses the default Python client name.
    request = urllib.request.Request(URL, headers={"User-Agent": "tfa-domains-update/1.0"})
    with urllib.request.urlopen(request, timeout=60) as response:
        sites = json.load(response)
    domains = set()
    for _name, site in sites:
        for value in [site.get("domain"), *site.get("additional-domains", [])]:
            host = clean(value) if value else None
            if host:
                domains.add(host)
    header = [
        "# Websites that offer authenticator app (TOTP) two-factor codes.",
        "# Source: 2FA Directory, https://2fa.directory (API v3, totp.json)",
        "# License: MIT, Copyright (c) 2021 2factorauth and Contributors",
        f"# Retrieved: {datetime.date.today().isoformat()}",
        "# Refresh with scripts/update-tfa-domains.py",
    ]
    OUT.write_text("\n".join(header + sorted(domains)) + "\n", encoding="utf-8")
    print(f"wrote {len(domains)} domains to {OUT}")


if __name__ == "__main__":
    main()
