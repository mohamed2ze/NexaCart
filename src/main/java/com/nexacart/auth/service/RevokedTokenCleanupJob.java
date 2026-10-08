package com.nexacart.auth.service;

import com.nexacart.auth.repository.RevokedTokenRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class RevokedTokenCleanupJob {

    private final RevokedTokenRepository revokedTokenRepository;

    public RevokedTokenCleanupJob(RevokedTokenRepository revokedTokenRepository) {
        this.revokedTokenRepository = revokedTokenRepository;
    }

    @Scheduled(fixedDelayString = "PT1H")
    @Transactional
    public void removeExpiredTokens() {
        revokedTokenRepository.deleteExpired(Instant.now());
    }
}
