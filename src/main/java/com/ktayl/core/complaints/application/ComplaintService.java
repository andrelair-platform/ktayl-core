package com.ktayl.core.complaints.application;

import com.ktayl.core.complaints.api.ComplaintOperations;
import com.ktayl.core.complaints.api.ComplaintSummary;
import com.ktayl.core.complaints.domain.Complaint;
import com.ktayl.core.complaints.domain.ComplaintChannel;
import com.ktayl.core.complaints.domain.ComplaintNotFoundException;
import com.ktayl.core.complaints.domain.ComplaintRepository;
import com.ktayl.core.complaints.domain.InvalidComplaintException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements the published {@link ComplaintOperations} port (ADR-005/013) by delegating to the internal use
 * cases ({@link RegisterComplaint}, {@link ComplaintLifecycle}) and mapping the domain aggregate to the
 * published {@link ComplaintSummary}. The String {@code channel} from the contract is parsed here — an
 * unknown value becomes {@link InvalidComplaintException} (→ 4xx), keeping the domain enum off the boundary.
 */
@Service
public class ComplaintService implements ComplaintOperations {

    private final RegisterComplaint registerComplaint;
    private final ComplaintLifecycle lifecycle;
    private final ComplaintRepository complaints;

    public ComplaintService(RegisterComplaint registerComplaint, ComplaintLifecycle lifecycle,
            ComplaintRepository complaints) {
        this.registerComplaint = registerComplaint;
        this.lifecycle = lifecycle;
        this.complaints = complaints;
    }

    @Override
    public UUID register(String channel, String subjectRef, String summary, String idempotencyKey,
            String actor) {
        var command = new RegisterComplaintCommand(parseChannel(channel), subjectRef, summary, idempotencyKey);
        return registerComplaint.handle(command, actor).complaintId();
    }

    @Override
    public void acknowledge(UUID id, String actor) {
        lifecycle.acknowledge(id, actor);
    }

    @Override
    public void assign(UUID id, String assignedTo, String actor) {
        lifecycle.assign(id, assignedTo, actor);
    }

    @Override
    public void resolve(UUID id, String resolutionNote, String actor) {
        lifecycle.resolve(id, resolutionNote, actor);
    }

    @Override
    public void close(UUID id, String actor) {
        lifecycle.close(id, actor);
    }

    @Override
    @Transactional(readOnly = true)
    public ComplaintSummary get(UUID id) {
        return complaints.findById(id).map(ComplaintService::toSummary)
                .orElseThrow(() -> new ComplaintNotFoundException(id));
    }

    private static ComplaintChannel parseChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            throw new InvalidComplaintException("channel is required");
        }
        try {
            return ComplaintChannel.valueOf(channel.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidComplaintException("unknown channel: " + channel);
        }
    }

    private static ComplaintSummary toSummary(Complaint c) {
        return new ComplaintSummary(c.id(), c.channel().name(), c.subjectRef(), c.summary(),
                c.status().name(), c.registeredAt(), c.acknowledgementDueAt(), c.assignedTo(),
                c.resolutionNote());
    }
}
