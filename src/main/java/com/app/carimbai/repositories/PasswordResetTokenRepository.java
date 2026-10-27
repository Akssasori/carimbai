package com.app.carimbai.repositories;

import com.app.carimbai.models.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Query("select t.staffUser.id from PasswordResetToken t where t.tokenHash = :tokenHash")
    Optional<Long> findStaffIdByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PasswordResetToken t where t.tokenHash = :tokenHash")
    Optional<PasswordResetToken> findByTokenHashForUpdate(String tokenHash);

    @Modifying
    @Query("update PasswordResetToken t set t.usedAt = :now where t.staffUser.id = :staffId and t.usedAt is null")
    int invalidateForStaff(Long staffId, OffsetDateTime now);
}
