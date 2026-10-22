
package com.locallink.backend.session;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionTokenRepository
        extends JpaRepository<SessionToken, UUID> {

    Optional<SessionToken> findByTokenHash(String tokenHash);

    List<SessionToken> findByDeviceId(UUID deviceId);
}
