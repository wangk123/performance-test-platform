package com.yr.perftest.platform.llm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PersistentModelDefinitionRepository extends JpaRepository<PersistentModelDefinitionRecord, Long> {
    List<PersistentModelDefinitionRecord> findAllByOrderByIdDesc();

    List<PersistentModelDefinitionRecord> findAllByProviderIdOrderByIdDesc(Long providerId);

    Optional<PersistentModelDefinitionRecord> findByProviderIdAndModelName(Long providerId, String modelName);

    long countByProviderId(Long providerId);

    void deleteAllByProviderId(Long providerId);

    List<PersistentModelDefinitionRecord> findAllByProviderIdAndEnabledTrueOrderByIdAsc(Long providerId);

    Optional<PersistentModelDefinitionRecord> findFirstByProviderIdAndEnabledTrueAndIsDefaultTrue(Long providerId);

    Optional<PersistentModelDefinitionRecord> findFirstByProviderIdAndEnabledTrueOrderByIdAsc(Long providerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update PersistentModelDefinitionRecord m set m.isDefault = false where m.isDefault = true")
    void clearAllDefaults();
}
