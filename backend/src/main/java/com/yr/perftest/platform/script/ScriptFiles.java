package com.yr.perftest.platform.script;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 脚本文件的读/原子写/静默删除，供脚本域各服务共用。 */
final class ScriptFiles {

    private ScriptFiles() {
    }

    static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new ScriptValidationException("failed to read script file");
        }
    }

    static void writeAtomically(Path target, String content) {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            throw new ScriptValidationException("failed to store script file");
        }
    }

    static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            // 删除失败不阻断业务（记录已删，孤儿文件可另行清理）
        }
    }
}
