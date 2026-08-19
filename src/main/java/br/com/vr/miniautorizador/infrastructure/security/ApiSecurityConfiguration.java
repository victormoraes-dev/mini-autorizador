package br.com.vr.miniautorizador.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
class ApiSecurityConfiguration {

        @Bean
        SecurityFilterChain apiSecurity(
                        HttpSecurity http,
                        ApiKeyAuthenticationFilter authenticationFilter,
                        ApiRateLimitFilter rateLimitFilter,
                        SecurityProblemWriter problemWriter) throws Exception {

                return http
                                .csrf(csrf -> csrf.disable())
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .authorizeHttpRequests(authorize -> authorize
                                                .requestMatchers("/actuator/health",
                                                                "/v3/api-docs/**",
                                                                "/swagger-ui/**")
                                                .permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/v1/cards/**")
                                                .hasRole("READER")
                                                .requestMatchers(HttpMethod.POST,
                                                                "/api/v1/cards",
                                                                "/api/v1/transactions")
                                                .hasRole("WRITER")
                                                .anyRequest().authenticated())
                                .exceptionHandling(errors -> errors
                                                .authenticationEntryPoint(
                                                                (request, response, exception) -> problemWriter
                                                                                .write(request,
                                                                                                response,
                                                                                                HttpStatus.UNAUTHORIZED
                                                                                                                .value(),
                                                                                                "authentication-required",
                                                                                                "Authentication required",
                                                                                                "A valid API key is required",
                                                                                                "AUTHENTICATION_REQUIRED"))
                                                .accessDeniedHandler(
                                                                (request, response, exception) -> problemWriter.write(
                                                                                request,
                                                                                response,
                                                                                HttpStatus.FORBIDDEN.value(),
                                                                                "access-denied",
                                                                                "Access denied",
                                                                                "The supplied API key cannot perform this operation",
                                                                                "ACCESS_DENIED")))
                                .addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter.class)
                                .addFilterAfter(rateLimitFilter, ApiKeyAuthenticationFilter.class)
                                .build();
        }
}
