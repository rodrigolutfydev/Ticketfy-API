package com.lutfy.ticketfy.infra.config;

import com.lutfy.ticketfy.infra.exception.ProblemException;
import com.lutfy.ticketfy.infra.exception.ProblemType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class PaginationGuard implements HandlerInterceptor {

    private final long maxPage;

    public PaginationGuard(@Value("${spring.data.web.pageable.max-page-size}") int maxPageSize) {
        this.maxPage = Integer.MAX_VALUE / maxPageSize;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        var page = request.getParameter("page");
        if (page != null && page.matches("\\d+") && (page.length() > 18 || Long.parseLong(page) > maxPage)) {
            throw invalid("page");
        }
        var sorts = request.getParameterValues("sort");
        if (sorts != null) {
            for (var sort : sorts) {
                if (sort.contains(".")) {
                    throw invalid("sort");
                }
            }
        }
        return true;
    }

    private static ProblemException invalid(String parameter) {
        return new ProblemException(ProblemType.INVALID_PARAMETER, "Invalid value for parameter '" + parameter + "'");
    }
}
