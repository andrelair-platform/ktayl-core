package com.ktayl.core.billing.api;

import com.ktayl.core.billing.invoicing.InvoiceService;
import com.ktayl.core.billing.persistence.InstallmentEntity;
import com.ktayl.core.billing.persistence.InstallmentRepository;
import com.ktayl.core.billing.persistence.InvoiceEntity;
import com.ktayl.core.billing.persistence.InvoiceRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Billing invoice API (SSO-gated — Finance). Raise an invoice (idempotent) + read who-owes-what. */
@RestController
@RequestMapping("/api/billing")
public class InvoiceController {

    public record InstallmentView(int seq, LocalDate dueDate, long amountMinor, String status) {}

    public record InvoiceView(String policyNumber, UUID invoiceId, String currency, long totalMinor,
            long outstandingMinor, String status, List<InstallmentView> installments) {}

    private final InvoiceService invoiceService;
    private final InvoiceRepository invoiceRepo;
    private final InstallmentRepository installmentRepo;

    public InvoiceController(InvoiceService invoiceService, InvoiceRepository invoiceRepo, InstallmentRepository installmentRepo) {
        this.invoiceService = invoiceService;
        this.invoiceRepo = invoiceRepo;
        this.installmentRepo = installmentRepo;
    }

    /** Raise the premium invoice for a policy. Idempotent: 201 if created, 200 if it already existed. */
    @PostMapping("/policies/{policyNumber}/invoice")
    public ResponseEntity<InvoiceView> raise(@PathVariable String policyNumber,
            @RequestParam(name = "installments", defaultValue = "1") int count) {
        var result = invoiceService.raiseFor(policyNumber, count);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(view(result.invoice()));
    }

    /** Who-owes-what: the invoice + installments + outstanding (Σ of open installments). */
    @GetMapping("/policies/{policyNumber}")
    public InvoiceView whoOwesWhat(@PathVariable String policyNumber) {
        InvoiceEntity inv = invoiceRepo.findByPolicyNumber(policyNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "no invoice for " + policyNumber));
        return view(inv);
    }

    private InvoiceView view(InvoiceEntity inv) {
        List<InstallmentEntity> items = installmentRepo.findByInvoiceIdOrderBySeq(inv.getId());
        long outstanding = items.stream()
                .filter(i -> "open".equals(i.getStatus()))
                .mapToLong(InstallmentEntity::getAmountMinor)
                .sum();
        List<InstallmentView> views = items.stream()
                .map(i -> new InstallmentView(i.getSeq(), i.getDueDate(), i.getAmountMinor(), i.getStatus()))
                .toList();
        return new InvoiceView(inv.getPolicyNumber(), inv.getId(), inv.getCurrency(), inv.getTotalMinor(),
                outstanding, inv.getStatus(), views);
    }
}
