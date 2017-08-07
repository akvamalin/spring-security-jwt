package com.mercateo.spring.security.jwt.config;

import java.util.Collections;
import java.util.Optional;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.auth0.jwt.JWTVerifier;
import com.mercateo.spring.security.jwt.JWTAuthenticationEntryPoint;
import com.mercateo.spring.security.jwt.JWTAuthenticationProvider;
import com.mercateo.spring.security.jwt.JWTAuthenticationSuccessHandler;
import com.mercateo.spring.security.jwt.JWTAuthenticationTokenFilter;
import com.mercateo.spring.security.jwt.verifier.JWTKeyset;
import com.mercateo.spring.security.jwt.verifier.JWTVerifierFactory;

import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@Slf4j
public class JWTSecurityConfiguration extends WebSecurityConfigurerAdapter {

    final Optional<JWTSecurityConfig> config;

    Optional<JWTVerifier> jwtVerifier;

    public JWTSecurityConfiguration(Optional<JWTSecurityConfig> config, Optional<JWTKeyset> jwtKeyset,
            JWTAuthenticationProvider authenticationProvider) {
        this.config = config;
        this.jwtKeyset = jwtKeyset;
        this.authenticationProvider = authenticationProvider;
    }

    @Bean
    public JWTAuthenticationEntryPoint jwtAuthenticationEntryPoint() {
        return new JWTAuthenticationEntryPoint();
    }

    private final Optional<JWTKeyset> jwtKeyset;

    private JWTAuthenticationProvider authenticationProvider;

    @Bean
    JWTVerifier verifier() {
        jwtVerifier = jwtKeyset
            .map(JWTVerifierFactory::new) //
            .map(JWTVerifierFactory::create);

        return jwtVerifier.orElse(null);
    }

    private static IllegalStateException map(Throwable cause) {
        return new IllegalStateException(cause);
    }

    @Bean
    @Override
    public AuthenticationManager authenticationManager() throws Exception {
        return new ProviderManager(Collections.singletonList(authenticationProvider));
    }

    public JWTAuthenticationTokenFilter authenticationTokenFilterBean() throws Exception {
        JWTAuthenticationTokenFilter authenticationTokenFilter = new JWTAuthenticationTokenFilter(Optional.ofNullable(
                verifier()));
        authenticationTokenFilter.setAuthenticationManager(authenticationManager());
        authenticationTokenFilter.setAuthenticationSuccessHandler(new JWTAuthenticationSuccessHandler());
        return authenticationTokenFilter;
    }

    @Override
    public void configure(HttpSecurity httpSecurity) throws Exception {

        final String[] unauthenticatedPaths = getUnauthenticatedPaths();

        log.info("with unauthenticated paths: {}", unauthenticatedPaths);
        httpSecurity
            // disable csrf
            .csrf()
            .disable()

            // allow
            .authorizeRequests()
            .antMatchers(unauthenticatedPaths)
            .permitAll()
            .and()

            // enable authorization
            .authorizeRequests()
            .anyRequest()
            .authenticated()
            .and()
            .exceptionHandling()
            .authenticationEntryPoint(jwtAuthenticationEntryPoint())
            .and()
            .sessionManagement()
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)

            // Custom JWT based security filter
            .and()
            .addFilterBefore(authenticationTokenFilterBean(), UsernamePasswordAuthenticationFilter.class)

            // disable page caching
            .headers()
            .cacheControl();
    }

    @Override
    public void configure(WebSecurity web) throws Exception {
        web.ignoring().antMatchers(getUnauthenticatedPaths());
    }

    private String[] getUnauthenticatedPaths() {
        return config.map(JWTSecurityConfig::anonymousPaths).map(list -> list.stream().toArray(String[]::new)).orElse(
                new String[0]);
    }

}