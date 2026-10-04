package com.cotizaia.config;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton pattern (project.txt section 6 pattern 3): one shared, process-wide
 * cache of hourly rates. The application never needs two rate caches, so a
 * single instance removes the risk of two parts of the app pricing a brief
 * with different numbers.
 *
 * <p><b>How the single instance is guaranteed.</b> GoF Singleton via the
 * initialization-on-demand holder idiom: the constructor is private and the
 * sole instance lives in the static {@code Holder}, created once by the class
 * loader on the first {@link #getInstance()} call. This is thread-safe without
 * explicit synchronization. {@code RateConfigurationBeans} exposes that same
 * instance as a Spring bean, so injected references and direct
 * {@code getInstance()} lookups resolve to one object.
 *
 * <p><b>What risk this contains.</b> Rates are edited by the agency owner while
 * the pricing pipeline reads them concurrently, and a role can carry several
 * rates over time (effective dating). Caching a single amount per role would
 * serve the wrong number outside that rate's window. The cache therefore stores
 * the whole <b>timeline</b> per role as an immutable list, and effective-date
 * selection happens on the cached data. Mutations call {@link #invalidate} (or
 * {@link #invalidateAgency}), so the only way stale data survives is if a
 * caller forgets to invalidate — {@code RateService} owns every write and does
 * not.
 */
public final class RateConfiguration {

    /**
     * Cache key is the agency + role pair, so two agencies can reuse a role
     * name without collisions. The value is the role's rate timeline.
     */
    private final Map<Long, Map<Long, List<CachedRate>>> cache = new ConcurrentHashMap<>();

    private RateConfiguration() {
    }

    private static final class Holder {
        private static final RateConfiguration INSTANCE = new RateConfiguration();
    }

    /**
     * Returns the single shared {@code RateConfiguration} instance.
     */
    public static RateConfiguration getInstance() {
        return Holder.INSTANCE;
    }

    /**
     * Returns the cached timeline for {@code roleId} inside {@code agencyId},
     * or {@code null} when it has not been loaded yet. A caller treating the
     * cache as read-through should fall back to the repository on {@code null}
     * and then call {@link #putTimeline}.
     */
    public List<CachedRate> get(Long agencyId, Long roleId) {
        Map<Long, List<CachedRate>> byRole = cache.get(agencyId);
        return byRole == null ? null : byRole.get(roleId);
    }

    /**
     * Resolves the amount valid at {@code at} from the cached timeline, or
     * {@code null} on a cache miss. When several rates are valid, the latest
     * one wins (the timeline is stored in insertion order).
     */
    public Long resolve(Long agencyId, Long roleId, Instant at) {
        List<CachedRate> timeline = get(agencyId, roleId);
        if (timeline == null) {
            return null;
        }
        Long match = null;
        for (CachedRate rate : timeline) {
            if (rate.isValidAt(at)) {
                match = rate.copPerHour();
            }
        }
        return match;
    }

    /**
     * Stores a loaded timeline. {@code computeIfAbsent} makes nested map
     * creation atomic under concurrent misses, the per-key locking that
     * replaces a hand-written double-checked lock. The list is copied so a
     * caller cannot mutate cached state afterwards.
     */
    public void putTimeline(Long agencyId, Long roleId, List<CachedRate> timeline) {
        cache.computeIfAbsent(agencyId, key -> new ConcurrentHashMap<>())
                .put(roleId, List.copyOf(timeline));
    }

    /**
     * Drops one role's cached timeline. Called by {@code RateService} after
     * every rate write, so the read-through cache self-heals on the next read.
     */
    public void invalidate(Long agencyId, Long roleId) {
        Map<Long, List<CachedRate>> byRole = cache.get(agencyId);
        if (byRole != null) {
            byRole.remove(roleId);
        }
    }

    /**
     * Drops the whole agency cache. Called when a role is deleted, so no orphan
     * key is left behind.
     */
    public void invalidateAgency(Long agencyId) {
        cache.remove(agencyId);
    }

    /**
     * Test/observability hook: current number of cached roles.
     */
    public int size() {
        return cache.values().stream().mapToInt(Map::size).sum();
    }

    public void clear() {
        cache.clear();
    }

    /**
     * Immutable cached view of one {@code rates} row: just the amount and the
     * optional effective window. Detaching the domain entity from the cache
     * avoids holding a JPA-managed object after its session closes.
     */
    public record CachedRate(long copPerHour, Instant effectiveFrom, Instant effectiveTo) {

        public boolean isValidAt(Instant instant) {
            boolean afterStart = effectiveFrom == null || !instant.isBefore(effectiveFrom);
            boolean beforeEnd = effectiveTo == null || instant.isBefore(effectiveTo);
            return afterStart && beforeEnd;
        }
    }
}
