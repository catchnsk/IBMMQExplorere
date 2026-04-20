package com.ibmexplorer.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import java.io.IOException;

@Configuration
public class WebMvcConfig {

    /**
     * Forwards all non-API, non-static resource requests to index.html
     * so that React Router can handle client-side navigation.
     */
    @Bean
    public FilterRegistrationBean<SpaForwardFilter> spaForwardFilter() {
        FilterRegistrationBean<SpaForwardFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new SpaForwardFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
        return registration;
    }

    public static class SpaForwardFilter implements Filter {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                throws IOException, ServletException {
            HttpServletRequest req = (HttpServletRequest) request;
            String uri = req.getRequestURI();
            String contextPath = req.getContextPath();
            String path = uri.substring(contextPath.length());

            // Forward to index.html only for routes that:
            // 1. Don't start with /api (REST endpoints)
            // 2. Don't start with /h2-console (DB admin)
            // 3. Don't contain a file extension (static assets)
            // 4. Don't start with /actuator
            boolean isApiPath = path.startsWith("/api/") || path.startsWith("/api");
            boolean isH2Path = path.startsWith("/h2-console");
            boolean isActuator = path.startsWith("/actuator");
            boolean isStaticFile = path.contains(".") && !path.endsWith("/");

            if (!isApiPath && !isH2Path && !isActuator && !isStaticFile && !path.equals("/")) {
                request.getRequestDispatcher("/index.html").forward(request, response);
                return;
            }

            chain.doFilter(request, response);
        }
    }
}
