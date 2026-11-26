package com.ktayl.core.finance.infrastructure.web;

import com.ktayl.core.finance.domain.IllegalPeriodTransitionException;
import com.ktayl.core.finance.domain.InvalidCloseRequestException;
import com.ktayl.core.finance.domain.InvalidPeriodException;
import com.ktayl.core.finance.domain.PeriodNotClosableException;
import com.ktayl.core.finance.domain.reserving.InvalidReserveRequestException;
import com.ktayl.core.finance.domain.reserving.ReserveReviewNotFoundException;
import com.ktayl.core.finance.domain.reserving.ReserveReviewNotPendingException;
import com.ktayl.core.finance.domain.reserving.SelfApprovalException;
import com.ktayl.core.finance.domain.reserving.TriangleNotFoundException;
import com.ktayl.core.finance.domain.reserving.TriangleShapeException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps the finance domain exceptions to HTTP (RFC-7807 {@link ProblemDetail}), scoped to this module's
 * controller package (close/reopen + reserving). Malformed period → 400; a close precondition / illegal
 * transition, a reserve self-approval, or a decision on a non-PENDING review → 409; a malformed request
 * (missing reason, a ragged triangle, a bad method/basis) → 422; a missing triangle/review → 404. None
 * surfaces as a 500.
 */
@RestControllerAdvice(basePackageClasses = FinanceController.class)
class FinanceExceptionHandler {

    @ExceptionHandler(InvalidPeriodException.class)
    ProblemDetail badRequest(InvalidPeriodException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler({PeriodNotClosableException.class, IllegalPeriodTransitionException.class,
            SelfApprovalException.class, ReserveReviewNotPendingException.class})
    ProblemDetail conflict(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler({InvalidCloseRequestException.class, InvalidReserveRequestException.class,
            TriangleShapeException.class})
    ProblemDetail unprocessable(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    }

    @ExceptionHandler({TriangleNotFoundException.class, ReserveReviewNotFoundException.class})
    ProblemDetail notFound(RuntimeException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }
}
