package com.taxdoc.planning.api;

import com.taxdoc.planning.extraction.ExtractionUnavailableException;
import com.taxdoc.planning.plan.NoDocumentsException;
import com.taxdoc.planning.plan.PlanNotFoundException;
import com.taxdoc.planning.rules.UnsupportedTaxYearException;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps errors to RFC 9457 problem responses. Extends Spring's handler so framework errors
 * (validation, unreadable JSON, unknown routes) get the same format as ours.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

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

    /** 422: the request is valid, but there is nothing to plan from. */
    @ExceptionHandler(NoDocumentsException.class)
    public ProblemDetail noDocuments(NoDocumentsException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(422), e.getMessage());
        problem.setTitle("No documents for client");
        problem.setProperty("clientRef", e.clientRef());
        problem.setProperty("taxYear", e.taxYear());
        return problem;
    }

    @ExceptionHandler(PlanNotFoundException.class)
    public ProblemDetail planNotFound(PlanNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Plan not found");
        return problem;
    }

    /** Adds which fields failed and why, e.g. {"errors": {"taxYear": "must not be null"}}. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new TreeMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail body = ex.getBody();
        body.setDetail("The request has invalid fields.");
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }
}
