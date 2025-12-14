package com.throttlex.middleware;

import com.throttlex.service.ThrottleXService;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class ThrottleXFilter extends OncePerRequestFilter {

    private final ThrottleXService service;

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain)
                                    throws ServletException, IOException {

        String key = service.extractKey(req);

        if (!service.check(key)) {
            res.setStatus(429);
            res.getWriter().write("Too Many Requests (ThrottleX)");
            return;
        }

        chain.doFilter(req, res);
    }
}
