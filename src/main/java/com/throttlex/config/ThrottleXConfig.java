package com.throttlex.config;

import com.throttlex.middleware.ThrottleXFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;

@Configuration
public class ThrottleXConfig {

    @Bean
    public FilterRegistrationBean<ThrottleXFilter> throttleFilter(ThrottleXFilter filter) {
        FilterRegistrationBean<ThrottleXFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(filter);
        reg.addUrlPatterns("/*");
        return reg;
    }
}
