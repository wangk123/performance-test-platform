package com.yr.perftest.platform.execution.distributed;

import com.yr.perftest.platform.execution.ExecutionValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class SshKeyService {
    private final Path keyPath;

    public SshKeyService(@Value("${platform.storage.root:./storage}") String storageRoot) {
        this.keyPath = Path.of(storageRoot).toAbsolutePath().normalize().resolve("keys").resolve("perftest_nodes");
    }

    public KeyMaterial ensureKey() {
        try {
            Files.createDirectories(keyPath.getParent());
            if (!Files.exists(keyPath) || !Files.exists(publicKeyPath())) {
                Process process = new ProcessBuilder(
                        "ssh-keygen",
                        "-t",
                        "rsa",
                        "-b",
                        "4096",
                        "-N",
                        "",
                        "-f",
                        keyPath.toString()
                ).redirectErrorStream(true).start();
                if (process.waitFor() != 0) {
                    throw new ExecutionValidationException("failed to generate ssh key");
                }
                new ProcessBuilder("chmod", "600", keyPath.toString()).start().waitFor();
            }
            return new KeyMaterial(keyPath.toString(), Files.readString(publicKeyPath(), StandardCharsets.UTF_8).trim());
        } catch (ExecutionValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ExecutionValidationException("failed to prepare ssh key");
        }
    }

    private Path publicKeyPath() {
        return Path.of(keyPath + ".pub");
    }

    public record KeyMaterial(String privateKeyPath, String publicKey) {
    }
}
