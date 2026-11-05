package ve.impulsatech.ahorramas.compartido.seguridad;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad transversal (rol del API Gateway + validación de JWT del C4):
 * stateless, tokens de vida corta, alcance por endpoint y anulación de sesiones de dispositivos revocados.
 */
@Configuration(proxyBeanMethods = false)
public class SeguridadConfig {

    @Bean
    SecurityFilterChain cadenaSeguridad(HttpSecurity http, JwtDecoder decoder) throws Exception {
        http.csrf(c -> c.disable())
                .cors(c -> { })
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/v1/auth/registro", "/api/v1/auth/login",
                                "/api/v1/consentimientos/callback", "/api/v1/backoffice/auth/login",
                                "/api/v1/demo/**", "/backoffice/**", "/actuator/health", "/actuator/info", "/error",
                                "/", "/index.html").permitAll()
                        .requestMatchers("/api/v1/saldo-rapido").hasAuthority("SCOPE_saldo:leer")
                        .requestMatchers("/api/v1/backoffice/**", "/actuator/modulith").hasAuthority("SCOPE_operador")
                        .anyRequest().hasAuthority("SCOPE_sesion"))
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.decoder(decoder).jwtAuthenticationConverter(convertidor()))
                        .authenticationEntryPoint((req, res, ex) -> escribirError(res, 401, "NO_AUTENTICADO", "Tu sesión no es válida o expiró"))
                        .accessDeniedHandler((req, res, ex) -> escribirError(res, 403, "PROHIBIDO", "No tienes permiso para esta operación")))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((req, res, ex) -> escribirError(res, 401, "NO_AUTENTICADO", "Se requiere autenticación"))
                        .accessDeniedHandler((req, res, ex) -> escribirError(res, 403, "PROHIBIDO", "No tienes permiso para esta operación")));
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${ahorra.seguridad.jwt-secreto}") String secreto, ObjectProvider<ValidadorSesion> validador) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(ServicioTokens.clave(secreto)).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> dispositivo = jwt -> {
            String tipo = jwt.getClaimAsString(ServicioTokens.TIPO);
            Object disp = jwt.getClaim(ServicioTokens.DISPOSITIVO);
            ValidadorSesion v = validador.getIfAvailable();
            if (v == null || disp == null || tipo == null || "operador".equals(tipo)) {
                return OAuth2TokenValidatorResult.success();
            }
            long idUsuario = Long.parseLong(jwt.getSubject());
            long idDispositivo = ((Number) disp).longValue();
            boolean valido = "saldo".equals(tipo) ? v.saldoRapidoActivo(idUsuario, idDispositivo) : v.dispositivoActivo(idUsuario, idDispositivo);
            return valido ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Dispositivo revocado", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer("ahorra-mas"), dispositivo));
        return decoder;
    }

    private static JwtAuthenticationConverter convertidor() {
        JwtGrantedAuthoritiesConverter alcances = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter c = new JwtAuthenticationConverter();
        c.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> autoridades = new ArrayList<>(alcances.convert(jwt));
            String rol = jwt.getClaimAsString(ServicioTokens.ROL);
            if (rol != null) {
                autoridades.add(new SimpleGrantedAuthority("ROL_" + rol));
            }
            return List.copyOf(autoridades);
        });
        return c;
    }

    static void escribirError(HttpServletResponse res, int status, String codigo, String mensaje) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"codigo\":\"" + codigo + "\",\"mensaje\":\"" + mensaje + "\",\"detalles\":{}}");
    }
}
