
package com.locallink.backend.room;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    Optional<Room> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select room from Room room where room.code = :code")
    Optional<Room> findByCodeForUpdate(@Param("code") String code);

    boolean existsByCode(String code);
}
