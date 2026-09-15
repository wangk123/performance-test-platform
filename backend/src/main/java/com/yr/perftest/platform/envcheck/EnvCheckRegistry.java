package com.yr.perftest.platform.envcheck;

import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 检查项注册表（spec §3.3）：Spring 收集全部 EnvCheckItem Bean；key 唯一。 */
@Component
public class EnvCheckRegistry {

    private final Map<String, EnvCheckItem> byKey;
    private final List<LocalCheckItem> locals;
    private final List<RemoteCheckItem> remotes;

    public EnvCheckRegistry(List<EnvCheckItem> items) {
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = items.stream().map(EnvCheckItem::key)
                .filter(k -> !seen.add(k)).collect(Collectors.toUnmodifiableSet());
        if (!duplicates.isEmpty()) {
            throw new IllegalStateException("env check item keys must be unique, got " + items.size()
                    + " items, duplicates: " + duplicates);
        }
        this.byKey = items.stream().collect(Collectors.toUnmodifiableMap(EnvCheckItem::key, Function.identity()));
        this.locals = items.stream().filter(i -> i instanceof LocalCheckItem).map(i -> (LocalCheckItem) i).toList();
        this.remotes = items.stream().filter(i -> i instanceof RemoteCheckItem).map(i -> (RemoteCheckItem) i).toList();
    }

    /** 全量检查项，按 category、sortOrder 排序。 */
    public List<EnvCheckItem> all() { return byKey.values().stream()
            .sorted(java.util.Comparator.comparing(EnvCheckItem::category).thenComparing(EnvCheckItem::sortOrder)).toList(); }

    public Optional<EnvCheckItem> byKey(String key) { return Optional.ofNullable(byKey.get(key)); }
    public List<LocalCheckItem> locals() { return locals; }
    public List<RemoteCheckItem> remotes() { return remotes; }

    /** 按 key 解析，保序、忽略未知 key。 */
    public List<EnvCheckItem> resolve(List<String> keys) {
        return keys == null ? List.of() : keys.stream().map(byKey::get).filter(java.util.Objects::nonNull).toList();
    }
}
