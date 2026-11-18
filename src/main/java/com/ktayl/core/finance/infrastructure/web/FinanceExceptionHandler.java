package com.ktayl.core.finance.infrastructure.web;

import com.ktayl.core.finance.domain.IllegalPeriodTransitionException;
import com.ktayl.core.finance.domain.InvalidCloseRequestException;
import com.ktayl.core.finance.domain.InvalidPeriodException;
import com.ktayl.core.finance.domain.PeriodNotClosableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the finance domain exceptions to HTTP (RFC-7807 {@link ProblemDetail}), scoped to this module's
 * controller package. Malformed period → 400; a close precondition / illegal transition → 409; a malformed
 * close/reopen request (missing reason) → 422. None surfaces as a 500.
 */
@RestControllerAdvice(basePackageClasses = FinanceController.class)
class FinanceExceptionHandler {

    @ExceptionHandler(InvalidPeriodException.class)
    ProblemDetail badRequest(InvalidPeriodException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler({PeriodNotClosableException.class, IllegalPeriodTransitionException.class})
    ProblemDetail conflict(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(InvalidCloseRequestException.class)
    ProblemDetail unprocessable(InvalidCloseRequestException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }
}
