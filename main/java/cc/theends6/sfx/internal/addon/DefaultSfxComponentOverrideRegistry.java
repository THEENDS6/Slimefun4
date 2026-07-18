package cc.theends6.sfx.internal.addon;

import cc.theends6.sfx.api.override.SfxComponentOverrideRegistrar;
import cc.theends6.sfx.api.override.SfxComponentOverrideTarget;
import cc.theends6.sfx.api.override.SfxComponentOverrideTargets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class DefaultSfxComponentOverrideRegistry {
    private static final Map<String, SfxComponentOverrideTarget<?>> KNOWN_TARGETS = Map.of(
            SfxComponentOverrideTargets.RESEARCH_PAYMENT.id(), SfxComponentOverrideTargets.RESEARCH_PAYMENT
    );

    private final Map<String, Claim> claims = new LinkedHashMap<>();
    private final Map<String, Registration<?>> registrations = new LinkedHashMap<>();

    public synchronized void claim(String addonId, String targetId, int contractVersion) {
        String owner = requireAddonId(addonId);
        SfxComponentOverrideTarget<?> target = KNOWN_TARGETS.get(targetId);
        if (target == null) {
            throw new IllegalStateException("Addon " + owner + " declares unknown component override target " + targetId);
        }
        if (target.contractVersion() != contractVersion) {
            throw new IllegalStateException("Addon " + owner + " declares " + targetId + " contract version "
                    + contractVersion + ", but SFX requires " + target.contractVersion());
        }
        Claim existing = claims.get(targetId);
        if (existing != null && !existing.addonId().equals(owner)) {
            throw new IllegalStateException("Component override target conflict: " + targetId
                    + " is declared by both " + existing.addonId() + " and " + owner);
        }
        claims.put(targetId, new Claim(owner, contractVersion));
    }

    public synchronized void validateImplementations() {
        for (Map.Entry<String, Claim> entry : claims.entrySet()) {
            Registration<?> registration = registrations.get(entry.getKey());
            if (registration == null || !registration.addonId().equals(entry.getValue().addonId())) {
                throw new IllegalStateException("Addon " + entry.getValue().addonId()
                        + " declared component override " + entry.getKey() + " but did not install an implementation");
            }
        }
    }

    public synchronized <T> Optional<T> implementation(SfxComponentOverrideTarget<T> target) {
        Registration<?> registration = registrations.get(target.id());
        if (registration == null) {
            return Optional.empty();
        }
        return Optional.of(target.contract().cast(registration.implementation()));
    }

    public synchronized void removeOwner(String addonId) {
        claims.entrySet().removeIf(entry -> entry.getValue().addonId().equals(addonId));
        registrations.entrySet().removeIf(entry -> entry.getValue().addonId().equals(addonId));
    }

    public synchronized void clear() {
        registrations.clear();
        claims.clear();
    }

    SfxComponentOverrideRegistrar registrarFor(String addonId) {
        String owner = requireAddonId(addonId);
        return new SfxComponentOverrideRegistrar() {
            @Override
            public <T> void replace(SfxComponentOverrideTarget<T> target, T implementation) {
                register(owner, target, implementation);
            }
        };
    }

    private synchronized <T> void register(String addonId, SfxComponentOverrideTarget<T> target, T implementation) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(implementation, "implementation");
        SfxComponentOverrideTarget<?> known = KNOWN_TARGETS.get(target.id());
        if (known == null || known.contractVersion() != target.contractVersion()
                || !known.contract().equals(target.contract())) {
            throw new IllegalStateException("Unsupported component override target contract: " + target.id());
        }
        Claim claim = claims.get(target.id());
        if (claim == null || !claim.addonId().equals(addonId)) {
            throw new IllegalStateException("Addon " + addonId + " attempted to replace " + target.id()
                    + " without declaring it in addon.yml");
        }
        if (!target.contract().isInstance(implementation)) {
            throw new IllegalArgumentException("Override implementation for " + target.id()
                    + " must implement " + target.contract().getName());
        }
        Registration<?> existing = registrations.get(target.id());
        if (existing != null) {
            throw new IllegalStateException("Component override target " + target.id()
                    + " already has an implementation from " + existing.addonId());
        }
        registrations.put(target.id(), new Registration<>(addonId, implementation));
    }

    private static String requireAddonId(String addonId) {
        if (addonId == null || addonId.isBlank()) {
            throw new IllegalArgumentException("Addon id must not be blank");
        }
        return addonId.trim();
    }

    public record OverrideDeclaration(String targetId, int contractVersion) {
        public OverrideDeclaration {
            if (targetId == null || targetId.isBlank()) {
                throw new IllegalArgumentException("Override target id must not be blank");
            }
            targetId = targetId.trim();
            if (contractVersion < 1) {
                throw new IllegalArgumentException("Override contract version must be at least 1");
            }
        }
    }

    private record Claim(String addonId, int contractVersion) {
    }

    private record Registration<T>(String addonId, T implementation) {
    }
}
