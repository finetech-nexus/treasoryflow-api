package fr.nbank.treasoryflow.persist;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class TreasuryStore {
    private final EntityManager em;

    public TreasuryStore(EntityManager em) {
        this.em = em;
    }

    public <T> T insert(T entity) {
        em.persist(entity);
        return entity;
    }

    public <T> Optional<T> find(Class<T> type, String id) {
        return Optional.ofNullable(em.find(type, id));
    }

    public <T> List<T> all(Class<T> type) {
        return em.createQuery("select e from " + type.getSimpleName() + " e", type).getResultList();
    }

    public <T> List<T> query(String jpql, Class<T> type, Map<String, Object> params) {
        TypedQuery<T> query = em.createQuery(jpql, type);
        params.forEach(query::setParameter);
        return query.getResultList();
    }

    public <T> Optional<T> first(String jpql, Class<T> type, Map<String, Object> params) {
        List<T> rows = query(jpql, type, params);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public void remove(Object entity) {
        em.remove(entity);
    }

    public void flush() {
        em.flush();
    }
}
