"""ktayl-core — C4 **Container** view (diagram-as-code).

mingrammer `diagrams` + Graphviz. Self-contained so it lives with the ktayl-core docs.
ktayl-core is an insurance-LOB **modular monolith** (Spring Boot + Spring Modulith, Java 21);
first module = Billing (premium→cash→GL). Facts from docs/architecture/solution-architecture.md
+ the ADRs + src/main/java package structure.

Run:  python ktayl_core_container.py   (needs: pip install diagrams ; brew/apt install graphviz)
Out:  ../architecture/img/ktayl-core-container.{png,svg}
"""
from __future__ import annotations

from pathlib import Path

from diagrams import Cluster, Diagram, Edge
from diagrams.custom import Custom
from diagrams.generic.blank import Blank
from diagrams.onprem.client import Users
from diagrams.onprem.compute import Server
from diagrams.onprem.database import PostgreSQL
from diagrams.onprem.identity import Dex
from diagrams.onprem.queue import Nats
from diagrams.programming.framework import Spring

# ----------------------------------------------------------------- theme (inlined)
SLATE, CYAN, GREEN, TEXT, MUTED, AMBER = "#0f172a", "#22d3ee", "#34d399", "#e2e8f0", "#94a3b8", "#f59e0b"
ICONS = Path(__file__).resolve().parent / "icons"
OUT = Path(__file__).resolve().parent.parent / "architecture" / "img"
OUT.mkdir(parents=True, exist_ok=True)


def node(cls, label, icon=None):
    p = ICONS / icon if icon else None
    return Custom(label, str(p)) if p and p.exists() else cls(label)


def cl(pencolor=CYAN):
    return {"bgcolor": "#111c33", "pencolor": pencolor, "fontcolor": pencolor,
            "fontname": "Helvetica-Bold", "fontsize": "13", "style": "rounded", "penwidth": "1.6"}


GRAPH = {"bgcolor": SLATE, "fontcolor": TEXT, "fontname": "Helvetica", "fontsize": "20",
         "pad": "0.6", "nodesep": "0.5", "ranksep": "0.9", "splines": "spline"}
NODE = {"fontcolor": TEXT, "fontname": "Helvetica", "fontsize": "11"}
EDGE = {"color": MUTED, "fontcolor": MUTED, "fontname": "Helvetica", "fontsize": "9"}


def flow(label=""):
    return Edge(label=label, color=CYAN, fontcolor=MUTED)


def event(label=""):
    return Edge(label=label, color=GREEN, fontcolor=MUTED)


def dep(label=""):
    return Edge(label=label, color=MUTED, fontcolor=MUTED, style="dashed")


with Diagram(
    "ktayl-core — C4 Container view  ·  insurance-LOB modular monolith  ·  Billing",
    filename=str(OUT / "ktayl-core-container"),
    outformat=["png", "svg"],
    direction="LR",
    show=False,
    graph_attr=GRAPH,
    node_attr=NODE,
    edge_attr=EDGE,
):
    finance = Users("Finance user\nAuthentik 'Finance' group")
    uw = Server("Underwriting & Pricing\n(#12) — bound-risk source")
    authentik = node(Dex, "Authentik\nOIDC resource-server", icon="authentik.png")

    with Cluster("Messaging  ·  shared infra", graph_attr=cl(GREEN)):
        nats = node(Nats, "NATS JetStream\nstream UNDERWRITING_EVENTS", icon="nats.png")

    with Cluster("ktayl-core  ·  ns ktayl-core  ·  Spring Boot 3.5 · Java 21 · :8080", graph_attr=cl(CYAN)):
        with Cluster("Spring Modulith modules (one deployable)", graph_attr=cl(AMBER)):
            ingest = Blank("billing.ingest\nUnderwritingEventConsumer\n(durable, manual-ack)")
            invoicing = Blank("billing.invoicing\nInvoiceService +\nInstallmentScheduler")
            api = Blank("billing.api\nREST (SSO, Finance)\n+ /webhooks/stripe")
            shared = Blank("shared\nOIDC · integration clients\n(cross-cutting)")
        pg = PostgreSQL("PostgreSQL (CNPG)\nschema 'billing'\ningested_policy · invoice ·\ninstallment · payment ·\nledger_outbox · audit_log")

    stripe = node(Spring, "Stripe API (test)\nSEPA Direct Debit", icon="stripe.png")
    erpnext = node(Spring, "ERPNext GL\nJournal Entries (double-entry)", icon="erpnext.png")

    # ---- flows ----
    uw >> event("bound-risk event") >> nats
    nats >> event("insurance.underwriting.\nbound-risk") >> ingest
    ingest >> event("PolicyIngested") >> invoicing
    ingest >> dep("upsert (idempotent)") >> pg
    invoicing >> dep("invoice + installments") >> pg

    finance >> flow("HTTPS") >> api
    authentik >> dep("validate JWT · Finance gate") >> api
    api >> dep("read/write") >> pg

    api >> flow("PaymentIntent · SEPA DD\nidempotency-key") >> stripe
    stripe >> flow("webhook\npayment_intent.succeeded\n(sig-verified)") >> api

    invoicing >> dep("Journal Entry\nvia ledger_outbox (async, retried)") >> erpnext
    api >> dep("capture → GL reconcile") >> erpnext
