package com.northstar.claims.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

@Configuration
public class SecurityConfiguration {
    @Bean
    AuthenticationManager authenticationManager() {
        // Prefix credentials are deliberately retained for migration parity, not recommended security.
        AuthenticationProvider provider = new AuthenticationProvider() {
            public Authentication authenticate(Authentication authentication) {
                String username = authentication.getName();
                String password = (String) authentication.getCredentials();
                if (username != null && password != null
                        && (("supervisor".equals(username) && "supervisor".equals(password))
                        || (username.startsWith("adjuster") && password.startsWith("legacy")))) {
                    return UsernamePasswordAuthenticationToken.authenticated(username, null,
                            AuthorityUtils.createAuthorityList("ROLE_OPERATOR"));
                }
                throw new BadCredentialsException("login.failed");
            }

            public boolean supports(Class<?> type) {
                return UsernamePasswordAuthenticationToken.class.isAssignableFrom(type);
            }
        };
        return new ProviderManager(provider);
    }

    @Bean
    HttpSessionSecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            HttpSessionSecurityContextRepository repository) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers(request -> request.getRequestURI().equals(request.getContextPath() + "/login.do")
                            || request.getRequestURI().endsWith(".css")
                            || request.getRequestURI().endsWith(".gif")
                            || request.getRequestURI().endsWith(".jpg")).permitAll()
                    .anyRequest().authenticated())
                .securityContext(context -> context.securityContextRepository(repository))
                .requestCache(cache -> cache.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(errors -> errors.authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(302);
                    response.setHeader("Location", request.getContextPath() + "/login.do");
                })).build();
    }
}
