package com.jobharvest.repository;

import com.jobharvest.model.Job;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRepository extends JpaRepository<Job, Long> {

    boolean existsBySourceAndExternalId(String source, Integer externalId);

    @Query("SELECT j FROM Job j WHERE " +
           "(CAST(:keyword AS string) IS NULL OR LOWER(j.title) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')) " +
           "OR LOWER(j.company) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))) " +
           "AND (CAST(:location AS string) IS NULL OR LOWER(j.location) LIKE LOWER(CONCAT('%', CAST(:location AS string), '%')))")
    Page<Job> findByFilters(@Param("keyword") String keyword,
                            @Param("location") String location,
                            Pageable pageable);
}
