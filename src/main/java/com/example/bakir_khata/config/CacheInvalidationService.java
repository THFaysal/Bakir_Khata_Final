package com.example.bakir_khata.config;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class CacheInvalidationService {
    private final CacheManager manager;

    public void communityFeedChanged() {
        afterCommit(() -> clear(ApplicationCacheConfig.OPEN_BROADCASTS));
    }

    public void borrowerRatingChanged(Long borrowerId) {
        if (borrowerId != null) {
            afterCommit(() -> {
                Cache cache = manager.getCache(ApplicationCacheConfig.BORROWER_RATINGS);
                if (cache != null) cache.evict(borrowerId);
            });
        }
    }

    public void allBorrowerRatingsChanged() {
        afterCommit(() -> clear(ApplicationCacheConfig.BORROWER_RATINGS));
    }

    private void clear(String name) {
        Cache cache = manager.getCache(name);
        if (cache != null) cache.clear();
    }

    private void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { task.run(); }
            });
        } else {
            // Support calls made outside a managed @Transactional method.
            task.run();
        }
    }
}
