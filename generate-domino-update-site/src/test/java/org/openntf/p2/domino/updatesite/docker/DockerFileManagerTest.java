package org.openntf.p2.domino.updatesite.docker;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DockerFileManagerTest {

    private static DockerFileManager dcc;

    @BeforeAll
    static void setUp() throws Exception {
        DockerFileManager.Builder dccBuilder = DockerFileManager.newBuilder()
                                                                .withImage("alpine:latest");

        dcc = dccBuilder.build();
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (dcc != null) {
            dcc.close();
        }
    }

    @Test
    void directoryExistsTest() throws DockerFileManagerException {
        assertTrue(dcc.directoryExists("/usr/bin"), "Directory should have been there");
        assertFalse(dcc.directoryExists("/usr/bin/doesnotexist"), "Directory should not have been there");
    }

    @Test
    void fileExistsTest() throws DockerFileManagerException {
        assertTrue(dcc.fileExists("/bin/busybox"), "File should have been there");
        assertFalse(dcc.fileExists("/usr/bin/doesnotexist"), "File should not have been there");
    }

    @Test
    void listFilesTest() throws DockerFileManagerException {
        assertThrowsExactly(
                DockerFileManagerException.class,
                () -> dcc.listFiles("/usr/bin/doesnotexist"),
                "Should throw exception when directory does not exist");

        List<String> files = dcc.listFiles("/etc");

        assertNotNull(files, "Files list should not be null");
        assertTrue(files.contains("alpine-release"), "Output should contain alpine-release");
    }

    @Test
    void downloadFileTest() throws DockerFileManagerException {
        assertThrowsExactly(
                DockerFileManagerException.class,
                () -> dcc.downloadFile("/usr/bin/doesnotexist"),
                "Should throw exception when file does not exist");

        assertThrowsExactly(
            DockerFileManagerException.class,
            () -> dcc.downloadFile("/etc"),
            "Should throw exception when given path is a directory");

        Path path = dcc.downloadFile("/etc/alpine-release");
        assertNotNull(path, "Path should not be null");
        assertTrue(path.toFile().exists(), "File should exist");
        assertTrue(path.toFile().length() > 0, "File should not be empty");

        // Test downloading the same file again
        Path path2 = dcc.downloadFile("/etc/alpine-release");
        assertNotNull(path2, "Path should not be null");
        assertNotEquals(path.toString(), path2.toString(), "Files should be downloaded to different locations");
    }

    @Test
    void downloadDirectoryTest() throws DockerFileManagerException {
        assertThrowsExactly(
                DockerFileManagerException.class,
                () -> dcc.downloadDirectory("/usr/bin/doesnotexist", false),
                "Should throw exception when directory does not exist");

        assertThrowsExactly(
            DockerFileManagerException.class,
            () -> dcc.downloadDirectory("/usr/etc/alpine-release", false),
            "Should throw exception when given path is a file");


        Path path = dcc.downloadDirectory("/etc", false);
        assertNotNull(path, "Path should not be null");
        assertTrue(path.toFile().exists(), "File should exist");
        assertEquals("etc.tar", path.getFileName().toString(), "File name should be etc.tar");
        assertTrue(path.toFile().length() > 0, "File should not be empty");

        Path path2 = dcc.downloadDirectory("/etc", false);
        assertNotNull(path2, "Path should not be null");
        assertNotEquals(path.toString(), path2.toString(), "Files should be downloaded to different locations");
    }

    @Test
    void downloadDirectoryWithUnpackTest() throws DockerFileManagerException {
        assertThrowsExactly(
                DockerFileManagerException.class,
                () -> dcc.downloadDirectory("/usr/bin/doesnotexist", true),
                "Should throw exception when directory does not exist");

        Path path = dcc.downloadDirectory("/etc", true);
        assertNotNull(path, "Path should not be null");
        assertTrue(path.toFile().exists(), "File should exist");
        assertTrue(path.toFile().isDirectory(), "Path should be a directory");
        assertTrue(Objects.requireNonNull(path.toFile().listFiles()).length > 0, "Directory should not be empty");
    }

}
