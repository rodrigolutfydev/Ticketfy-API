package com.lutfy.ticketfy.infra.security;

import com.lutfy.ticketfy.infra.exception.ProblemDetailResponseWriter;
import com.lutfy.ticketfy.infra.exception.ProblemType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemDetailResponseWriter writer;

    public JsonAccessDeniedHandler(ProblemDetailResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        writer.write(request, response, ProblemType.ACCESS_DENIED, "Access denied");
    }
}
