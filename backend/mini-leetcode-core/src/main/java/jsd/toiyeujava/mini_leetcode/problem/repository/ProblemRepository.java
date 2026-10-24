package jsd.toiyeujava.mini_leetcode.problem.repository;

import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProblemRepository extends JpaRepository<Problem, Long>, JpaSpecificationExecutor<Problem> {
    Optional<Problem> findBySlug(String slug);
    boolean existsBySlug(String slug);
}
