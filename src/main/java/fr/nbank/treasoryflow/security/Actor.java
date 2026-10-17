package fr.nbank.treasoryflow.security;

import java.util.Set;

public record Actor(String id, Set<String> roles, Set<String> entities) {
    public boolean has(String role) {
        return roles.contains("ADMIN") || roles.contains(role);
    }

    public boolean canSee(String entityId) {
        return entities.contains("*") || entities.contains(entityId);
    }
}
