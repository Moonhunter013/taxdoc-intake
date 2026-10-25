package com.taxdoc.planning.api;

import com.taxdoc.planning.extraction.ExtractionUnavailableException;
import com.taxdoc.planning.rules.UnsupportedTaxYearException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps domain exceptions to RFC 9457 problem responses. */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * 422, not 400: the request is well-formed, but we can't process that tax year. The
     * response lists the supported years so the client can correct the request.
     */
    @ExceptionHandler(UnsupportedTaxYearException.class)
    public ProblemDetail unsupportedTaxYear(UnsupportedTaxYearException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(422), e.getMessage());
        problem.setTitle("Unsupported tax year");
        problem.setProperty("taxYear", e.taxYear());
        problem.setProperty("supportedYears", e.supportedYears());
        return problem;
    }

    /**
     * 503: we depend on extraction-service and it couldn't answer. {@code reason} says whether it
     * timed out, was unreachable or sent a bad response; the cause's details stay in the log.
     */
    @ExceptionHandler(ExtractionUnavailableException.class)
    public ProblemDetail extractionUnavailable(ExtractionUnavailableException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        problem.setTitle("Extraction service unavailable");
        problem.setProperty("reason", e.reason().name());
        return problem;
    }
}
