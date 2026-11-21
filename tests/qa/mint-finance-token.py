# pyright: reportMissingImports=false
# Mint signed JWT access tokens for the Billing (commission-split) live QA gate, via Authentik's provider
# signer. RUNS INSIDE the authentik pod via `ak shell` (the `authentik.*` imports resolve there, not
# locally). Emits TFINANCE (user 100001 forced into the 'Finance' group — the InvoiceController gate) and
# TNOGROUP (no gate group) on stdout. Invoked by billing-commission-qa-gate.sh. Never echo the tokens.
from authentik.providers.oauth2.models import OAuth2Provider
from authentik.core.models import User
import time

prov = OAuth2Provider.objects.get(name="ktayl-core")


def mint(username, groups):
    u = User.objects.get(username=username)
    now = int(time.time())
    payload = {
        "iss": "https://auth.devandre.sbs/application/o/ktayl-core/",
        "sub": u.uid, "aud": prov.client_id,
        "iat": now, "exp": now + 3600, "auth_time": now,
        "preferred_username": u.username, "email": u.email or (u.username + "@ktayl.local"),
        "groups": groups,
    }
    return prov.encode(payload)


print("TFINANCE=" + mint("100001", ["Finance"]))
print("TNOGROUP=" + mint("100001", ["Users"]))
