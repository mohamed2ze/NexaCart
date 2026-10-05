package com.nexacart.auth.repository;

import com.nexacart.auth.entity.RevokedToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface RevokedTokenRepository extends JpaRepository<RevokedToken, UUID> {

    boolean existsByJti(String jti);

    @Modifying
    @Query(value = """
            insert into revoked_tokens (id, jti, user_id, expires_at, created_at, updated_at)
            values (:id, :jti, :userId, :expiresAt, :now, :now)
            on conflict (jti) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("id") UUID id,
            @Param("jti") String jti,
            @Param("userId") UUID userId,
            @Param("expiresAt") Instant expiresAt,
            @Param("now") Instant now
    );

    @Modifying
    @Query("delete from RevokedToken r where r.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
