package com.example.tickets.ticket;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    Optional<Ticket> findByKey(String key);

    @Query("select distinct t from Ticket t left join fetch t.comments where t.key = :key")
    Optional<Ticket> findWithCommentsByKey(@Param("key") String key);

    @Query("select t.key from Ticket t order by t.id")
    List<String> findAllKeys();

    @Query(value = "select nextval('ticket_key_seq')", nativeQuery = true)
    long nextKeyNumber();

    /** Case-insensitive substring match on title or description (OQ-7), AND-ed with an optional status (FR-8). */
    static Specification<Ticket> matching(String keyword, TicketStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (keyword != null) {
                String pattern = "%" + escapeLike(keyword.toLowerCase()) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern, '\\'),
                        cb.like(cb.lower(root.get("description")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
