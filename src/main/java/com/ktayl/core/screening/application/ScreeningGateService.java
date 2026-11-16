package com.ktayl.core.screening.application;

import com.ktayl.core.screening.api.PartyRef;
import com.ktayl.core.screening.api.ScreeningGate;
import com.ktayl.core.screening.api.ScreeningOutcome;
import com.ktayl.core.screening.domain.Party;
import org.springframework.stereotype.Service;

/**
 * The published {@link ScreeningGate} port's implementation — the in-process entry point policy / billing
 * will call at a gate in S2. It is a thin adapter over {@link ScreenParty}: maps the published {@link PartyRef}
 * DTO to the domain {@link Party}, delegates, and maps the domain {@link ScreenParty.Outcome} back to the
 * published {@link ScreeningOutcome} (verdict as a String). No business rule — those live in the use case /
 * domain.
 */
@Service
public class ScreeningGateService implements ScreeningGate {

    private final ScreenParty screenParty;

    public ScreeningGateService(ScreenParty screenParty) {
        this.screenParty = screenParty;
    }

    @Override
    public ScreeningOutcome screen(PartyRef party, String gate, String actor) {
        Party domainParty = new Party(null, party.externalRef(), party.name(), party.dob(),
                party.nationality(), party.residence(), null, null);
        ScreenParty.Outcome outcome = screenParty.screen(domainParty, gate, actor);
        return new ScreeningOutcome(outcome.verdict().name(), outcome.alertId(), outcome.datasetVersion());
    }
}
