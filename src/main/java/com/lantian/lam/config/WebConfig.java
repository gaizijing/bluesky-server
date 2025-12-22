package com.lantian.lam.config;

import com.lantian.lam.filter.RequestLoggingFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebConfig {

    @Bean
    public RequestLoggingFilter requestResponseLoggingFilter() {
        return new RequestLoggingFilter();
    }
}
