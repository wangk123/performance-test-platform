package com.yr.perftest.platform.envcheck;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 注册表：key 唯一校验 + resolve 忽略未知 key（spec §3.3）。 */
class EnvCheckRegistryTest {

    private record StubItem(String key, EnvCheckKind kind) implements EnvCheckItem {
        public String label() { return key; }
        public String description() { return ""; }
        public EnvCheckCategory category() { return EnvCheckCategory.OS; }
        public java.util.Set<String> appliesTo() { return java.util.Set.of(); }
        public int sortOrder() { return 0; }
    }

    @Test
    void duplicateKeysRejected() {
        var a = new StubItem("os.ulimit", EnvCheckKind.LOCAL);
        var b = new StubItem("os.ulimit", EnvCheckKind.REMOTE);
        assertThatThrownBy(() -> new EnvCheckRegistry(List.of(a, b)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("os.ulimit");
    }

    @Test
    void resolveIgnoresUnknownKeepsOrder() {
        var a = new StubItem("doc.metrics-defined", EnvCheckKind.LOCAL);
        var b = new StubItem("os.ulimit", EnvCheckKind.REMOTE);
        var registry = new EnvCheckRegistry(List.of(b, a));
        assertThat(registry.resolve(List.of("doc.metrics-defined", "gone", "os.ulimit"))
                .stream().map(EnvCheckItem::key))
                .containsExactly("doc.metrics-defined", "os.ulimit");
    }
}
