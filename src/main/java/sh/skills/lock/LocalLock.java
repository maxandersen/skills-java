package sh.skills.lock;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import sh.skills.model.SkillLockEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Manages the project-level local-lock.json file.
 * Tracks project-level skill installations.
 * Mirrors local-lock.ts from the TypeScript source.
 */
public class LocalLock {

    private final Path lockFile;
    private final ObjectMapper mapper;

    public LocalLock(Path projectDir) {
        this.lockFile = projectDir.resolve("local-lock.json");
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public Map<String, SkillLockEntry> readAll() {
        if (!Files.exists(lockFile)) return new HashMap<>();
        try {
            return mapper.readValue(lockFile.toFile(),
                new TypeReference<Map<String, SkillLockEntry>>() {});
        } catch (IOException e) {
            return new HashMap<>();
        }
    }

    public void write(String agent, String skillName, SkillLockEntry entry) throws IOException {
        Map<String, SkillLockEntry> all = readAll();
        all.put(agent + ":" + skillName, entry);
        save(all);
    }

    public void remove(String agent, String skillName) throws IOException {
        Map<String, SkillLockEntry> all = readAll();
        all.remove(agent + ":" + skillName);
        save(all);
    }

    public SkillLockEntry get(String agent, String skillName) {
        return readAll().get(agent + ":" + skillName);
    }

    public Path getLockFile() { return lockFile; }

    private void save(Map<String, SkillLockEntry> data) throws IOException {
        Files.createDirectories(lockFile.getParent());
        mapper.writeValue(lockFile.toFile(), data);
    }

    /**
     * Get the local lock file path for a given directory.
     * If dir is null, uses the current working directory.
     */
    public static Path getLocalLockPath(Path dir) {
        Path baseDir = dir != null ? dir : Paths.get(System.getProperty("user.dir"));
        return baseDir.resolve("local-lock.json");
    }

    /**
     * Remove a skill from the local lock file.
     * Returns true if the skill was found and removed, false otherwise.
     */
    public static boolean removeSkillFromLocalLock(Path dir, String skillId) throws IOException {
        LocalLock lock = new LocalLock(dir);
        Map<String, SkillLockEntry> entries = lock.readAll();
        if (entries.containsKey(skillId)) {
            entries.remove(skillId);
            lock.save(entries);
            return true;
        }
        return false;
    }

    /**
     * Compute a SHA-256 hash of all files in a skill folder.
     * Excludes .git and node_modules directories.
     * Returns a hex string of the hash.
     */
    public static String computeSkillFolderHash(Path skillDir) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            // Walk the directory tree and hash all files
            try (Stream<Path> paths = Files.walk(skillDir)) {
                paths
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String s = p.toString().replace('\\', '/');
                        return !s.contains("/.git/") && !s.contains("/node_modules/");
                    })
                    .sorted() // Ensure consistent ordering
                    .forEach(path -> {
                        try {
                            // Hash the relative path
                            // Normalize separators for consistent cross-platform hashes
                            String relativePath = skillDir.relativize(path).toString().replace('\\', '/');
                            digest.update(relativePath.getBytes(StandardCharsets.UTF_8));
                            // Hash the file content
                            digest.update(Files.readAllBytes(path));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
            }

            // Convert to hex string
            byte[] hashBytes = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
