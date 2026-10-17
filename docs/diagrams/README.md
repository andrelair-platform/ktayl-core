# ktayl-core architecture diagrams (diagram-as-code)

C4 views of ktayl-core written as Python with [mingrammer `diagrams`](https://diagrams.mingrammer.com/)
(Graphviz), kept **with the docs** so they never drift. Part of the platform-wide diagram-as-code
standard (see `minicloud-platform-docs/diagrams/`).

## Generate

```bash
brew install graphviz          # or: apt-get install -y graphviz   (system dep)
python3 -m venv .venv && . .venv/bin/activate
pip install -r requirements.txt
python ktayl_core_container.py   # → ../architecture/img/ktayl-core-container.{png,svg}
```

The image is committed to `docs/architecture/img/` and embedded in
`docs/architecture/c4-container.md` (and surfaced on the central docs site per the
documentation standard).

## Files

- `ktayl_core_container.py` — C4 **container** view (the Spring Boot modular monolith, its Spring
  Modulith modules, CNPG, NATS JetStream ingest, Stripe, ERPNext GL, Authentik).
- `icons/` — brand PNGs with no built-in `diagrams` icon (Stripe, ERPNext, Authentik, NATS).

To add a brand icon: drop a transparent PNG in `icons/` and pass `icon="name.png"` to `node()`.
Commit the regenerated image alongside the source change.
