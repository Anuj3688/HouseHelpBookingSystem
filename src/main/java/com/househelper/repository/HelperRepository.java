package com.househelper.repository;

import com.househelper.model.Helper;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface HelperRepository extends JpaRepository<Helper, UUID>, JpaSpecificationExecutor<Helper> {

    Optional<Helper> findByPhone(String phone);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select helper from Helper helper where helper.id = :helperId")
    Optional<Helper> findByIdForUpdate(@Param("helperId") UUID helperId);
}
