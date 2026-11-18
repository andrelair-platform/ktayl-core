package com.ktayl.core.finance.infrastructure.web;

import com.ktayl.core.finance.domain.InvalidPeriodException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the finance domain exceptions to HTTP (RFC-7807 {@link ProblemDetail}), scoped to this module's
 * controller package. A malformed accounting period is a 400 (bad input), never a 500.
 */
@RestControllerAdvice(basePackageClasses = FinanceController.class)
class FinanceExceptionHandler {

    @ExceptionHandler(InvalidPeriodException.class)
    ProblemDetail badRequest(InvalidPeriodException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}
