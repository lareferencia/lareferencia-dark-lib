package org.lareferencia.contrib.dark.services;

import org.springframework.stereotype.Component;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.security.spec.*;
import java.util.*;

/** Filesystem-only custody of private keys. No private material is returned by the management API. */
@Component
public class DarkKeyStorage {
    public record Material(PrivateKey privateKey, PublicKey publicKey, String publicKeySsh, String keyId) { }

    public static Path validateRoot(String root, String allowedRoot) {
        Path allowed = Path.of(allowedRoot).toAbsolutePath().normalize();
        Path requested = Path.of(root).toAbsolutePath().normalize();
        if (!requested.startsWith(allowed)) throw new IllegalArgumentException("Key storage must be inside the deployment storage boundary");
        // Resolve system ancestors (e.g. macOS /var) but reject links at and below the managed boundary.
        for (Path current = requested; current != null && current.startsWith(allowed); current = current.getParent())
            if (Files.isSymbolicLink(current)) throw new IllegalArgumentException("Symbolic links are not allowed in key storage paths");
        try {
            Path ancestor = allowed.getParent();
            while (ancestor != null && !Files.exists(ancestor)) ancestor = ancestor.getParent();
            if (ancestor == null) throw new IllegalArgumentException("Storage ancestor is unavailable");
            return ancestor.toRealPath().resolve(ancestor.relativize(requested));
        } catch (java.io.IOException e) { throw new IllegalArgumentException("Storage path cannot be resolved", e); }
    }

    public String generate(String root, String allowedRoot) {
        try {
            Path directory = validateRoot(root, allowedRoot);
            createDirectories(directory);
            checkPermissions(directory, true);
            String id = UUID.randomUUID().toString();
            Path target = directory.resolve(id);
            Path temporary = Files.createTempDirectory(directory, ".generating-", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            try {
                KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
                String pem = "-----BEGIN PRIVATE KEY-----\n" + Base64.getMimeEncoder(64, new byte[]{10}).encodeToString(pair.getPrivate().getEncoded())
                        + "\n-----END PRIVATE KEY-----\n";
                write(temporary.resolve("private.pk8.pem"), pem.getBytes(StandardCharsets.US_ASCII));
                write(temporary.resolve("public.der"), pair.getPublic().getEncoded());
                write(temporary.resolve("public.pub"), (sshPublicKey(pair.getPublic()) + "\n").getBytes(StandardCharsets.US_ASCII));
                String manifest = "{\"version\":1,\"credentialId\":\"" + id + "\",\"keyId\":\""
                        + DarkRequestSigner.sha256(rawPublicKey(pair.getPublic())) + "\",\"createdAt\":\""
                        + java.time.Instant.now() + "\"}\n";
                write(temporary.resolve("manifest.json"), manifest.getBytes(StandardCharsets.UTF_8));
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
                load(root, allowedRoot, id);
                return id;
            } finally {
                if (Files.exists(temporary)) {
                    try (var entries = Files.list(temporary)) {
                        for (Path file : entries.toList()) Files.deleteIfExists(file);
                    }
                    Files.deleteIfExists(temporary);
                }
            }
        } catch (java.io.IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Unable to generate Ed25519 credentials in server storage", e);
        }
    }

    public Material load(String root, String allowedRoot, String id) {
        try {
            Path folder = requireAvailable(root, allowedRoot, id);
            Path privateFile = folder.resolve("private.pk8.pem");
            Path publicFile = folder.resolve("public.der");
            rejectSymlinks(privateFile);
            rejectSymlinks(publicFile);
            checkPermissions(privateFile, false);
            if (!Files.isRegularFile(privateFile, LinkOption.NOFOLLOW_LINKS) || !Files.isRegularFile(publicFile, LinkOption.NOFOLLOW_LINKS))
                throw new IllegalStateException("Credential files are unavailable");
            String pem = Files.readString(privateFile, StandardCharsets.US_ASCII);
            byte[] encoded = Base64.getMimeDecoder().decode(pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", ""));
            KeyFactory factory = KeyFactory.getInstance("Ed25519");
            PrivateKey privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(encoded));
            PublicKey publicKey = factory.generatePublic(new X509EncodedKeySpec(Files.readAllBytes(publicFile)));
            byte[] challenge = "dARK credential pair verification".getBytes(StandardCharsets.UTF_8);
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(privateKey); signature.update(challenge); byte[] signed = signature.sign();
            signature.initVerify(publicKey); signature.update(challenge);
            if (!signature.verify(signed)) throw new IllegalStateException("Credential public and private keys do not match");
            return new Material(privateKey, publicKey, sshPublicKey(publicKey), DarkRequestSigner.sha256(rawPublicKey(publicKey)));
        } catch (java.io.IOException | GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Unable to load valid Ed25519 credentials from server storage", e);
        }
    }

    /** Cheap check on every send; parsing and cryptographic pair validation remain cached. */
    public Path requireAvailable(String root, String allowedRoot, String id) {
        try {
            if (id == null || !UUID.fromString(id).toString().equals(id)) throw new IllegalArgumentException("Invalid credential ID");
            Path directory = validateRoot(root, allowedRoot);
            checkPermissions(directory, true);
            Path folder = directory.resolve(id);
            rejectSymlinks(folder); checkPermissions(folder, true);
            for (String name : List.of("private.pk8.pem", "public.der")) {
                Path file = folder.resolve(name);
                rejectSymlinks(file);
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(file))
                    throw new IllegalStateException("Credential files are unavailable");
                checkPermissions(file, false);
            }
            return folder;
        } catch (java.io.IOException | IllegalArgumentException e) {
            throw new IllegalStateException("Credential storage is unavailable or invalid", e);
        }
    }

    public static byte[] rawPublicKey(PublicKey key) {
        byte[] encoded = key.getEncoded();
        byte[] prefix = HexFormat.of().parseHex("302a300506032b6570032100");
        if (encoded.length != prefix.length + 32 || !Arrays.equals(prefix, Arrays.copyOf(encoded, prefix.length)))
            throw new IllegalArgumentException("An Ed25519 public key is required");
        return Arrays.copyOfRange(encoded, prefix.length, encoded.length);
    }

    private static String sshPublicKey(PublicKey key) throws java.io.IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        byte[] algorithm = "ssh-ed25519".getBytes(StandardCharsets.US_ASCII);
        out.writeInt(algorithm.length); out.write(algorithm);
        byte[] raw = rawPublicKey(key); out.writeInt(raw.length); out.write(raw);
        return "ssh-ed25519 " + Base64.getEncoder().encodeToString(bytes.toByteArray()) + " dark-minter";
    }

    private static void write(Path file, byte[] bytes) throws java.io.IOException {
        Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.write(file, bytes);
    }

    private static void createDirectories(Path path) throws java.io.IOException {
        rejectSymlinks(path);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            Path parent = path.getParent();
            if (parent != null) createDirectories(parent);
            Files.createDirectory(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        }
    }

    private static void checkPermissions(Path path, boolean directory) throws java.io.IOException {
        var permissions = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
        if (permissions.stream().anyMatch(permission -> permission.name().startsWith("GROUP_") || permission.name().startsWith("OTHERS_")))
            throw new IllegalStateException("Credential storage permissions must be " + (directory ? "0700" : "0600"));
    }

    private static void rejectSymlinks(Path path) {
        for (Path current = path; current != null; current = current.getParent())
            if (Files.isSymbolicLink(current)) throw new IllegalArgumentException("Symbolic links are not allowed in key storage paths");
    }
}
