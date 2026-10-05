package com.nexacart.address.repository;

import com.nexacart.address.entity.Address;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddressRepository extends JpaRepository<Address, UUID> {

    List<Address> findByUserIdOrderByDefaultAddressDescCreatedAtDesc(UUID userId);

    Optional<Address> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);

    boolean existsByUserIdAndDefaultAddressTrue(UUID userId);

    @Modifying
    @Query("update Address a set a.defaultAddress = false where a.user.id = :userId and a.defaultAddress = true")
    int clearDefaultForUser(@Param("userId") UUID userId);
}
