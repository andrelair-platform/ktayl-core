"""ktayl insurance SI — TARGET architecture (diagram-as-code), layered view.

Adapted from the InsureMO reference middle-office model (UI/UX → BFF → Business/Orchestration →
Atomic services → Integration/Data), re-cut onto ktayl's decisions: ONE modular-monolith core
(ktayl-core, Spring Modulith) instead of atomic micro-services, with the orchestration/service
layer kept as an in-process module layer (NOT a deployable). Justified satellites + adopted COTS
sit beside the core. A LIVING diagram — extend as contexts/capabilities land.

Facts: modernization-roadmap.md + phase-0-inventory.md + ADR-004..011.
Run:  python ktayl_si_target.py   (needs: pip install diagrams ; brew/apt install graphviz)
Out:  ../architecture/img/ktayl-si-target.{png,svg}
"""
from __future__ import annotations

from pathlib import Path

from diagrams import Cluster, Diagram, Edge
from diagrams.generic.blank import Blank
from diagrams.onprem.client import Users
from diagrams.onprem.compute import Server
from diagrams.onprem.database import PostgreSQL
from diagrams.onprem.queue import Nats
from diagrams.programming.framework import Spring

# ----------------------------------------------------------------- theme (matches ktayl_core_container.py)
SLATE, CYAN, GREEN, TEXT, MUTED, AMBER = "#0f172a", "#22d3ee", "#34d399", "#e2e8f0", "#94a3b8", "#f59e0b"
VIOLET, ROSE = "#a78bfa", "#fb7185"
OUT = Path(__file__).resolve().parent.parent / "architecture" / "img"
OUT.mkdir(parents=True, exist_ok=True)


def cl(pencolor=CYAN):
    return {"bgcolor": "#111c33", "pencolor": pencolor, "fontcolor": pencolor,
            "fontname": "Helvetica-Bold", "fontsize": "13", "style": "rounded", "penwidth": "1.6"}


GRAPH = {"bgcolor": SLATE, "fontcolor": TEXT, "fontname": "Helvetica", "fontsize": "20",
         "pad": "0.6", "nodesep": "0.4", "ranksep": "0.9", "splines": "spline"}
NODE = {"fontcolor": TEXT, "fontname": "Helvetica", "fontsize": "11"}
EDGE = {"color": MUTED, "fontcolor": MUTED, "fontname": "Helvetica", "fontsize": "9"}


def flow(label=""):
    return Edge(label=label, color=CYAN, fontcolor=MUTED)


def event(label=""):
    return Edge(label=label, color=GREEN, fontcolor=MUTED)


def dep(label=""):
    return Edge(label=label, color=MUTED, fontcolor=MUTED, style="dashed")


with Diagram(
    "ktayl insurance SI — TARGET  ·  modular-monolith core + justified satellites + adopted COTS",
    filename=str(OUT / "ktayl-si-target"),
    outformat=["png", "svg"],
    direction="TB",
    show=False,
    graph_attr=GRAPH,
    node_attr=NODE,
    edge_attr=EDGE,
):
    # ---------------------------------------------------------------- UI/UX — channels + lifecycle apps
    with Cluster("UI/UX  ·  channels + full policy life-cycle (Next.js)", graph_attr=cl(GREEN)):
        agent = Users("Agent / Broker")
        customer = Users("Customer")
        staff = Users("Staff / Underwriter / Adjuster")
        with Cluster("lifecycle surfaces", graph_attr=cl(MUTED)):
            apps = Blank("New Business · Underwriting ·\nCustomer Service · Claims")

    # ---------------------------------------------------------------- BFF per channel
    with Cluster("BFF  ·  per-channel API shaping (server-side)", graph_attr=cl(AMBER)):
        bff_agent = Server("agent-BFF")
        bff_cust = Server("customer-BFF")

    # ---------------------------------------------------------------- ktayl-core (THE monolith)
    with Cluster("ktayl-core  ·  ONE deployable · Spring Modulith · Java 21 · 2–3 replicas", graph_attr=cl(CYAN)):

        with Cluster("Application / Orchestration layer  (in-process — NOT a service)", graph_attr=cl(VIOLET)):
            orch = Blank("Process / Transaction services\n(bind, FNOL, invoice-run) +\npublished context APIs + domain events")

        with Cluster("Bounded-context modules  (schema-per-context, api/internal/events)", graph_attr=cl(AMBER)):
            policy = Blank("Product & Policy (PAS)\n★ Product Configure\n(products as DATA, ADR-011)")
            uw = Blank("Underwriting\n(rating / referral)")
            claims = Blank("Claims\n(FNOL→settle)")
            billing = Blank("Billing & Finance\n(premium→cash→GL)")
            more = Blank("Distribution · Reinsurance ·\nCompliance · Documents ·\nRisk-Eng · Intl · Complaints …")

        with Cluster("Shared kernel / foundation  (in-core)", graph_attr=cl(GREEN)):
            party = Blank("Party / Customer MDM\n(golden record — shared kernel)")
            foundation = Blank("Foundation: numbering ·\nvalidation · audit · money")

    # ---------------------------------------------------------------- Justified satellites
    with Cluster("Justified satellites  (separate — a real force, ADR-008)", graph_attr=cl(ROSE)):
        iam = Server("ktayl-iam\nidentity / IGA")
        ai = Server("AI services\n(RAG · agents · OCR/IDP — Python/GPU)")
        bridge = Server("legacy-CDC bridge\n(globalcore, frozen)")
        retrieva = Server("Retrieva\n(DORA product — the cert)")

    # ---------------------------------------------------------------- Adopted COTS
    with Cluster("Adopted COTS  (never build)", graph_attr=cl(MUTED)):
        erp = Server("ERPNext\nGL / HR")
        glpi = Server("GLPI\nITSM")
        workplace = Server("Workplace\n(mail/files/chat/sign)")
        alm = Server("Investment / ALM\nengine")

    # ---------------------------------------------------------------- Integration (NOT an ESB)
    with Cluster("Integration  ·  NATS + gateway + n8n + ACL adapters  (NOT an ESB)", graph_attr=cl(GREEN)):
        nats = Nats("NATS / JetStream\nintegration events")
        gw = Server("ingress-nginx + Authentik\n(API gateway)")

    # ---------------------------------------------------------------- Data
    with Cluster("Data  ·  one CNPG cluster, schema-per-context + PITR→R2", graph_attr=cl(CYAN)):
        db = PostgreSQL("CNPG\nparty · policy · claims ·\nunderwriting · billing …")

    # ---------------------------------------------------------------- edges (principal flows)
    agent >> flow() >> bff_agent
    customer >> flow() >> bff_cust
    staff >> flow() >> apps
    apps >> flow() >> bff_agent
    [bff_agent, bff_cust] >> flow("REST / published API") >> orch
    orch >> flow("in-process calls") >> [policy, uw, claims, billing, more]
    [policy, uw, claims, billing] >> dep("read by id (shared kernel)") >> party
    [policy, uw, claims, billing] >> dep() >> foundation
    orch >> flow("identity") >> iam
    orch >> event("integration events") >> nats
    nats >> event() >> [ai, bridge, retrieva, erp]
    orch >> dep("integrate") >> [glpi, workplace, alm]
    gw >> flow() >> orch
    [policy, uw, claims, billing, party, foundation] >> dep() >> db
