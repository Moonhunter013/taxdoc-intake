package com.taxdoc.planning.api;

import com.taxdoc.planning.rules.UnsupportedTaxYearException;
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
}
