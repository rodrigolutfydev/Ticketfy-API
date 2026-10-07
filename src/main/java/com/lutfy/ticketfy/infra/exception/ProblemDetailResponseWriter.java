package com.lutfy.ticketfy.infra.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

@Component
public class ProblemDetailResponseWriter {

    private final JsonMapper jsonMapper;
    private final ProblemDetailFactory factory;

    public ProblemDetailResponseWriter(JsonMapper jsonMapper, ProblemDetailFactory factory) {
        this.jsonMapper = jsonMapper.rebuild()
                .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
                .build();
        this.factory = factory;
    }

    public void write(HttpServletRequest request, HttpServletResponse response,
                      ProblemType type, String detail) throws IOException {
        var problem = factory.create(type, detail, request.getRequestURI());
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getWriter(), problem);
    }
}
