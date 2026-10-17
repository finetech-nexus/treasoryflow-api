package fr.nbank.treasoryflow.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ActorFilter extends OncePerRequestFilter {
    private static final Set<String> ROLES = Set.of("TREASURER", "VALIDATOR", "COMPLIANCE", "AUDITOR", "ADMIN");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }
        String actorId = trim(request.getHeader("X-Actor"));
        String rolesHeader = trim(request.getHeader("X-Roles"));
        String scopeHeader = trim(request.getHeader("X-Entity-Scope"));
        if (actorId.isBlank() || rolesHeader.isBlank() || scopeHeader.isBlank()) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "En-têtes X-Actor, X-Roles et X-Entity-Scope requis");
            return;
        }
        Set<String> roles = Arrays.stream(rolesHeader.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (roles.isEmpty() || !ROLES.containsAll(roles)) {
            response.sendError(HttpStatus.BAD_REQUEST.value(), "Rôle inconnu");
            return;
        }
        Set<String> entities = Arrays.stream(scopeHeader.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (entities.isEmpty()) {
            response.sendError(HttpStatus.BAD_REQUEST.value(), "Périmètre vide");
            return;
        }
        ActorContext.set(new Actor(actorId, roles, entities));
        try {
            chain.doFilter(request, response);
        } finally {
            ActorContext.clear();
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
