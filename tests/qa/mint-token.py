# pyright: reportMissingImports=false
# Mint signed JWT access tokens for the Complaints live QA gate, via Authentik's provider signer.
# RUNS INSIDE the authentik pod via `ak shell` (the `authentik.*` imports resolve there, not locally).
# Emits THANDLER (user 100001 with their real groups incl 'Complaints Handler') and TNOGROUP (no gate
# group) on stdout. Invoked by complaints-qa-gate.sh. Never echo the tokens to a shared transcript.
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


real = sorted(g.name for g in User.objects.get(username="100001").ak_groups.all())
print("THANDLER=" + mint("100001", real))
print("TNOGROUP=" + mint("100001", ["Users"]))
