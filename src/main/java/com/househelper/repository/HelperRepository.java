package com.househelper.repository;

import com.househelper.model.Helper;
import com.househelper.model.SkillType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface HelperRepository extends JpaRepository<Helper, Long> {

    Optional<Helper> findByPhone(String phone);

    @Query(value = """
            select distinct h
            from Helper h
            join h.localities locality
            join h.skills skill
            where lower(locality) = lower(:locality)
              and skill = :skill
            """,
            countQuery = """
                    select count(distinct h)
                    from Helper h
                    join h.localities locality
                    join h.skills skill
                    where lower(locality) = lower(:locality)
                      and skill = :skill
                    """)
    Page<Helper> searchHelpers(@Param("locality") String locality,
                               @Param("skill") SkillType skill,
                               Pageable pageable);
}
