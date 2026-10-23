package dev.pricemonitor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/** Кабинет на одного администратора: логин и пароль из переменных окружения. */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/demo-shop/**", "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/", true).permitAll())
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"));
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    public UserDetailsService users(MonitorProperties props, PasswordEncoder encoder) {
        if (props.adminUsername() == null || props.adminUsername().isBlank()
                || props.adminPassword() == null || props.adminPassword().isBlank()) {
            throw new IllegalStateException("Задайте ADMIN_USERNAME и ADMIN_PASSWORD");
        }
        return new InMemoryUserDetailsManager(User.withUsername(props.adminUsername())
                .password(encoder.encode(props.adminPassword()))
                .roles("ADMIN")
                .build());
    }
}
