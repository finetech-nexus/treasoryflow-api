package fr.nbank.treasoryflow.security;

public final class ActorContext {
    private static final ThreadLocal<Actor> CURRENT = new ThreadLocal<>();

    private ActorContext() {}

    public static Actor current() {
        Actor actor = CURRENT.get();
        if (actor == null) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Acteur requis");
        }
        return actor;
    }

    static void set(Actor actor) {
        CURRENT.set(actor);
    }

    static void clear() {
        CURRENT.remove();
    }
}
