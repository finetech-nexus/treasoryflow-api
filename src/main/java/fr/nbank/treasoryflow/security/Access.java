package fr.nbank.treasoryflow.security;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class Access {
    public Actor current() {
        return ActorContext.current();
    }

    public void require(String role) {
        Actor actor = current();
        if (!actor.has(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Rôle requis : " + role);
        }
    }

    public void requireAny(String... roles) {
        Actor actor = current();
        for (String role : roles) {
            if (actor.has(role)) {
                return;
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Rôle insuffisant");
    }

    public void entity(String entityId) {
        if (!current().canSee(entityId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Périmètre " + entityId + " non autorisé");
        }
    }

    public void fourEyes(String maker) {
        if (current().id().equals(maker)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Séparation des tâches : l'auteur ne peut pas approuver sa propre opération");
        }
    }

    public List<String> visible(List<String> entityIds) {
        Actor actor = current();
        if (actor.entities().contains("*")) {
            return entityIds;
        }
        return entityIds.stream().filter(actor::canSee).toList();
    }
}
