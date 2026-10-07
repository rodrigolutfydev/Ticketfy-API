package com.lutfy.ticketfy.infra.exception;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;

@Component
public class ProblemDetailFactory {

    private final String baseUri;

    public ProblemDetailFactory(@Value("${ticketfy.problems.base-uri}") String baseUri) {
        this.baseUri = baseUri.endsWith("/") ? baseUri : baseUri + "/";
    }

    public ProblemDetail create(ProblemType type, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(type.status(), detail);
        problem.setType(URI.create(baseUri + type.slug()));
        problem.setTitle(type.title());
        return problem;
    }

    public ProblemDetail create(ProblemType type, String detail, String path) {
        var problem = create(type, detail);
        problem.setInstance(instanceOf(path));
        return problem;
    }

    public ProblemDetail createGeneric(HttpStatus status, String path) {
        var problem = ProblemDetail.forStatusAndDetail(status, status.getReasonPhrase());
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(instanceOf(path));
        return problem;
    }

    public ResponseEntity<ProblemDetail> response(ProblemType type, String detail) {
        return response(create(type, detail));
    }

    public ResponseEntity<ProblemDetail> response(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    private static URI instanceOf(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            return URI.create(path);
        } catch (IllegalArgumentException ex) {
            return URI.create(UriUtils.encodePath(path, StandardCharsets.UTF_8));
        }
    }
}
