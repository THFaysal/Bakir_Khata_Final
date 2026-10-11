package com.example.bakir_khata.community;

import com.example.bakir_khata.config.ApplicationCacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CachedCommunityFeed {
    private final LoanBroadcastRepository broadcasts;
    private final CommunityProfileRepository profiles;

    @Cacheable(cacheNames = ApplicationCacheConfig.OPEN_BROADCASTS, key = "'open'", sync = true)
    @Transactional(readOnly = true)
    public List<PublicBroadcastView> openBroadcasts() {
        List<LoanBroadcast> rows = broadcasts.findByStatusOrderByCreatedAtDesc(LoanBroadcast.Status.OPEN);
        List<Long> owners = rows.stream().map(b -> b.getOwner().getId()).distinct().toList();
        Map<Long, CommunityProfile> publicProfiles = owners.isEmpty() ? Map.of() :
                profiles.findByUserIdIn(owners).stream().collect(Collectors.toMap(
                        p -> p.getUser().getId(), Function.identity(), (a, b) -> a));
        return rows.stream().map(b -> {
            Long ownerId = b.getOwner().getId();
            CommunityProfile profile = publicProfiles.get(ownerId);
            return new PublicBroadcastView(b.getId(), ownerId,
                    profile == null ? "unknown" : profile.getMessengerId(),
                    profile == null ? "Community member" : profile.getDisplayName(),
                    b.getAmount(), b.getPurpose(), b.getDescription(), b.getDurationDays(),
                    b.getStatus(), b.getExpiresAt());
        }).toList();
    }
}
