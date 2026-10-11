package org.lareferencia.contrib.dark.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;
import static org.junit.jupiter.api.Assertions.*;

class DarkKeyStorageTest {
    @TempDir Path directory;
    private final DarkKeyStorage storage = new DarkKeyStorage();

    @Test
    void generatesReloadablePairWithRawFingerprintAndRestrictedPrivateFile() throws Exception {
        Path root = directory.resolve("keys");
        String id = storage.generate(root.toString(), directory.toString());
        var material = storage.load(root.toString(), directory.toString(), id);
        assertEquals(32, DarkKeyStorage.rawPublicKey(material.publicKey()).length);
        assertEquals(64, material.keyId().length());
        assertTrue(material.publicKeySsh().startsWith("ssh-ed25519 "));
        byte[] blob = Base64.getDecoder().decode(material.publicKeySsh().split(" ")[1]);
        assertArrayEquals(DarkKeyStorage.rawPublicKey(material.publicKey()), java.util.Arrays.copyOfRange(blob, blob.length - 32, blob.length));
        assertEquals(PosixFilePermissions.fromString("rw-------"), Files.getPosixFilePermissions(root.resolve(id).resolve("private.pk8.pem")));
        String next = storage.generate(root.toString(), directory.toString());
        assertNotEquals(id, next);
        assertNotEquals(material.keyId(), storage.load(root.toString(), directory.toString(), next).keyId());
    }

    @Test
    void rejectsMismatchedPairsAndReadablePrivateFiles() throws Exception {
        Path root = directory.resolve("keys");
        String first = storage.generate(root.toString(), directory.toString());
        String second = storage.generate(root.toString(), directory.toString());
        Files.copy(root.resolve(second).resolve("public.der"), root.resolve(first).resolve("public.der"), StandardCopyOption.REPLACE_EXISTING);
        assertThrows(IllegalStateException.class, () -> storage.load(root.toString(), directory.toString(), first));
        Files.setPosixFilePermissions(root.resolve(second).resolve("private.pk8.pem"), PosixFilePermissions.fromString("rw-r--r--"));
        assertThrows(IllegalStateException.class, () -> storage.load(root.toString(), directory.toString(), second));
    }

    @Test
    void rejectsEscapingPathsAndSymlinks() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> DarkKeyStorage.validateRoot(directory.resolve("../outside").toString(), directory.toString()));
        Path link = directory.resolve("link");
        Files.createSymbolicLink(link, directory);
        assertThrows(IllegalArgumentException.class, () -> DarkKeyStorage.validateRoot(link.resolve("keys").toString(), directory.toString()));
        assertThrows(IllegalStateException.class, () -> storage.load(directory.toString(), directory.toString(), "../private"));
    }
}
