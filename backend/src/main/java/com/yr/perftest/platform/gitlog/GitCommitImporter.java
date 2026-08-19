package com.yr.perftest.platform.gitlog;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Git 提交导入（模块 10）：用 JGit 拉取仓库到本地存储并遍历分支提交快照。
 */
@Component
public class GitCommitImporter {
    private final String storageRoot;

    public GitCommitImporter(@Value("${platform.storage.root:./storage}") String storageRoot) {
        this.storageRoot = storageRoot;
    }

    public List<PersistentGitCommitRecord> importCommits(PersistentGitRepositoryRecord repository, String branch) {
        Path cloneDir = Path.of(storageRoot, "git-repos", Long.toString(repository.getId()));
        try {
            File gitDir = Files.isDirectory(cloneDir)
                    ? refresh(cloneDir, branch)
                    : clone(repository, branch, cloneDir);
            return walk(gitDir, repository.getId(), branch);
        } catch (Exception exception) {
            throw new IllegalStateException("git import failed: " + exception.getMessage());
        }
    }

    private File refresh(Path cloneDir, String branch) throws GitAPIException, IOException {
        try (Git git = Git.open(cloneDir.toFile())) {
            git.fetch().call();
            return git.getRepository().getDirectory();
        }
    }

    private File clone(PersistentGitRepositoryRecord repository, String branch, Path cloneDir)
            throws GitAPIException, IOException {
        Files.createDirectories(cloneDir.getParent());
        try (Git git = Git.cloneRepository()
                .setURI(repository.getUrl())
                .setBranch(branch)
                .setDirectory(cloneDir.toFile())
                .call()) {
            return git.getRepository().getDirectory();
        }
    }

    private List<PersistentGitCommitRecord> walk(File gitDir, long repositoryId, String branch)
            throws IOException {
        try (org.eclipse.jgit.lib.Repository repo = new FileRepositoryBuilder()
                .setGitDir(gitDir)
                .readEnvironment()
                .findGitDir()
                .build()) {
            List<PersistentGitCommitRecord> records = new ArrayList<>();
            String ref = "refs/heads/" + branch;
            ObjectId head = repo.resolve(ref);
            if (head == null) {
                head = repo.resolve("refs/remotes/origin/" + branch);
            }
            if (head == null) {
                return records;
            }
            try (RevWalk revWalk = new RevWalk(repo)) {
                revWalk.markStart(revWalk.parseCommit(head));
                int limit = 500;
                for (RevCommit commit : revWalk) {
                    if (records.size() >= limit) {
                        break;
                    }
                    records.add(new PersistentGitCommitRecord(
                            repositoryId,
                            branch,
                            commit.getId().name(),
                            commit.getShortMessage(),
                            commit.getAuthorIdent().getName(),
                            Instant.ofEpochSecond(commit.getCommitTime())
                    ));
                }
            }
            return records;
        }
    }
}
