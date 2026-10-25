package com.taxdoc.planning.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<Plan, String> {

    /** Loads a plan with its results in one query (results are lazy by default). */
    @EntityGraph(attributePaths = "results")
    Optional<Plan> findWithResultsById(String id);

    List<Plan> findByClientRefOrderByCreatedAtDesc(String clientRef);
}
