package com.lutfy.ticketfy.infra.exception;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class ProblemDetailErrorController implements ErrorController {

    private static final Map<HttpStatus, ProblemType> GENERIC_TYPES = Map.of(
            HttpStatus.BAD_REQUEST, ProblemType.BAD_REQUEST,
            HttpStatus.UNAUTHORIZED, ProblemType.AUTHENTICATION_REQUIRED,
            HttpStatus.FORBIDDEN, ProblemType.ACCESS_DENIED,
            HttpStatus.NOT_FOUND, ProblemType.RESOURCE_NOT_FOUND,
            HttpStatus.METHOD_NOT_ALLOWED, ProblemType.METHOD_NOT_ALLOWED,
            HttpStatus.UNSUPPORTED_MEDIA_TYPE, ProblemType.UNSUPPORTED_MEDIA_TYPE,
            HttpStatus.INTERNAL_SERVER_ERROR, ProblemType.INTERNAL_ERROR
    );

    private final ProblemDetailFactory problems;

    public ProblemDetailErrorController(ProblemDetailFactory problems) {
        this.problems = problems;
    }

    @RequestMapping("${server.error.path:/error}")
    public ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
        var status = resolveStatus(request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE));
        var path = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI) instanceof String uri ? uri : null;
        var type = GENERIC_TYPES.get(status);
        var problem = type != null
                ? problems.create(type, type.title(), path)
                : problems.createGeneric(status, path);
        return problems.response(problem);
    }

    private static HttpStatus resolveStatus(Object code) {
        if (code instanceof Integer value) {
            var status = HttpStatus.resolve(value);
            if (status != null && status.isError()) {
                return status;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
