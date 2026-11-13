package com.ktayl.core.complaints.infrastructure.web;

import com.ktayl.core.complaints.domain.ComplaintNotFoundException;
import com.ktayl.core.complaints.domain.IllegalComplaintTransitionException;
import com.ktayl.core.complaints.domain.InvalidComplaintException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the complaints domain exceptions to HTTP (RFC-7807 {@link ProblemDetail}), scoped to this module's
 * controller package. This is where the domain's typed failures become status codes — the domain itself
 * carries no HTTP concept (ADR-013). Malformed JSON / wrong content-type are left to Spring's defaults
 * (400/415), so a bad request never surfaces as a 500.
 */
@RestControllerAdvice(basePackageClasses = ComplaintController.class)
class ComplaintExceptionHandler {

    @ExceptionHandler(ComplaintNotFoundException.class)
    ProblemDetail notFound(ComplaintNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(IllegalComplaintTransitionException.class)
    ProblemDetail conflict(IllegalComplaintTransitionException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvalidComplaintException.class)
    ProblemDetail unprocessable(InvalidComplaintException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }
}
