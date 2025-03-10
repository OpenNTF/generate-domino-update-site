package org.openntf.p2.domino.updatesite.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.openntf.p2.domino.updatesite.docker.DockerUtils.ExecResult;

public class DockerFileManager implements AutoCloseable {

    private final String containerId;
    private final boolean removeContainerAfterUse;
    private final Path targetPath;
    private final DockerClient dockerClient;

    /**
     * Constructor for DockerFileManager. Use builder pattern to create an instance.
     * @param dockerClient DockerClient is created using the builder pattern.
     * @param imageId the image ID to create a container from. If null, the containerId must be provided.
     * @param containerId the container ID to use. If null, a new container will be created from the imageId.
     * @throws DockerFileManagerException if there is an error creating the DockerFileManager
     */
    private DockerFileManager(DockerClient dockerClient, String imageId, String containerId) throws DockerFileManagerException {
        this.dockerClient = dockerClient;

        if (StringUtils.isAllEmpty(imageId, containerId)) {
            throw new IllegalArgumentException("Either imageId or containerId must be provided.");
        }

        if (StringUtils.isNotEmpty(imageId)) {
            this.containerId = DockerUtils.createContainer(dockerClient, imageId);
            this.removeContainerAfterUse = true;
            System.out.println("Container created: " + this.containerId);
        } else {
            this.containerId = containerId;
            this.removeContainerAfterUse = false;
        }

        try {
            this.targetPath = Files.createTempDirectory(Paths.get(System.getProperty("java.io.tmpdir")), "domupdsite");
            System.out.println("Temporary directory created: " + targetPath);
        } catch (IOException e) {
            throw new DockerFileManagerException("Unable to create temp directory", e);
        }
    }

    @Override
    public void close() throws Exception {
        if (removeContainerAfterUse) {
            // It means the container is temprary and we need to remove it
            DockerUtils.stopRemoveContainer(dockerClient, containerId);
        }

        dockerClient.close();

        // Delete the temporary directory and its contents
        try (Stream<Path> paths = Files.walk(targetPath)) {
            paths.sorted(Comparator.reverseOrder())
                 .forEach(path -> {
                     try {
                         Files.delete(path);
                     } catch (IOException e) {
                         System.err.println("Unable to delete file: " + path + " - " + e.getMessage());
                     }
                 });
        }
    }

    public Path downloadFile(String filePath) throws DockerFileManagerException {
        if (fileExists(filePath)) {
            String fileName = Paths.get(filePath).getFileName().toString();

            try {
                // Create a temporary directory to store the downloaded file
                Path tempPath = Files.createTempDirectory(targetPath, "fd");
                Path targetFilePath = tempPath.resolve(fileName);

                InputStream is = dockerClient.copyArchiveFromContainerCmd(containerId, filePath).exec();

                Files.copy(is, targetFilePath, StandardCopyOption.REPLACE_EXISTING);

                return targetFilePath;

            } catch (IOException e) {
                throw new DockerFileManagerException("Unable to copy file", e);
            }
        } else {
            throw new DockerFileManagerException("File does not exist: " + filePath);
        }
    }

    public Path downloadDirectory(String directoryPath) throws DockerFileManagerException {
        if (directoryExists(directoryPath)) {

            try {
                // Create a temporary directory to store the downloaded file
                Path tempPath = Files.createTempDirectory(targetPath, "fd");

                // Download to a zip file
                String zipFileName = Paths.get(directoryPath).getFileName().toString() + ".zip";

                Path targetZipPath = tempPath.resolve(zipFileName);
                InputStream is = dockerClient.copyArchiveFromContainerCmd(containerId, directoryPath).exec();
                Files.copy(is, targetZipPath, StandardCopyOption.REPLACE_EXISTING);

                return targetZipPath;
            } catch (IOException e) {
                throw new DockerFileManagerException("Unable to copy directory", e);
            }

        } else {
            throw new DockerFileManagerException("Directory does not exist: " + directoryPath);
        }
    }

    /**
     * List files in a directory inside the container. Those ends with a / are directories.
     *
     * @param directoryPath path to the directory inside the container
     * @return list of files in the directory
     * @throws DockerFileManagerException if there is an error executing the command
     */
    public List<String> listFiles(String directoryPath) throws DockerFileManagerException {
        String output = sendShellCommand("ls -FA1 $1", directoryPath);

        return Arrays.stream(output.split("\n"))
                     .map(String::trim)
                     .collect(Collectors.toList());
    }

    /**
     * Check if a directory exists inside the container.
     *
     * @param directoryPath path to the directory inside the container
     * @return true if the directory exists, false otherwise
     * @throws DockerFileManagerException if there is an error executing the command
     */
    public boolean directoryExists(String directoryPath) throws DockerFileManagerException {
        String output = sendShellCommand("[ -d \"$1\" ] && echo 1 || echo 0", directoryPath);

        return "1".equals(output);
    }

    /**
     * Check if a file exists inside the container.
     *
     * @param filePath path to the file inside the container
     * @return true if the file exists, false otherwise
     * @throws DockerFileManagerException if there is an error executing the command
     */
    public boolean fileExists(String filePath) throws DockerFileManagerException {
        String output = sendShellCommand("[ -f \"$1\" ] && echo 1 || echo 0", filePath);
        return "1".equals(output);
    }

    /**
     * Execute a shell command inside the container.
     *
     * @param cmd    the command to execute. Use $1, $2, ... for parameters
     * @param params the parameters to pass to the command
     * @return the output of the command
     * @throws DockerFileManagerException if there is an error executing the command
     */
    private String sendShellCommand(String cmd, String... params) throws DockerFileManagerException {
        // We add a placeholder for $0 (script name)
        String[] base = ArrayUtils.toArray("sh", "-c", cmd, "dockerFmScript");
        String[] cmdArray = ArrayUtils.addAll(base, params);

        ExecResult result = DockerUtils.execCmd(dockerClient, containerId, cmdArray);

        if (result.isError()) {
            throw new DockerFileManagerException("Shell error in Docker: " + result.getError());
        }

        return result.getOutput().trim();
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {

        private String containerId;
        private String imageId;

        Builder() {
        }

        /**
         * Set the container ID to use. If null, a new container will be created from the imageId.
         *
         * @param containerId the container ID to use
         * @return this builder
         */
        public Builder withContainer(String containerId) {
            this.containerId = containerId;
            return this;
        }

        /**
         * Set the image ID to create a container from. If given, a new container with an empty entrypoint will be created.
         * IF image doesn't exist, it will be pulled from the appropriate repository. So imageId would better be a full image name.
         * @param image the image ID to create a container from
         * @return this builder
         */
        public Builder withImage(String image) {
            this.imageId = image;
            return this;
        }

        private DockerClient createDockerClient() {
            // Docker client configuration
            DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                                                                        //.withDockerContext("maggie-socket")
                                                                        //.withDockerHost("tcp://maggie.developi.info:2375")
                                                                        .build();

            DockerHttpClient dockerHttpClientClient = new ApacheDockerHttpClient.Builder()
                .dockerHost(config.getDockerHost())
                .sslConfig(config.getSSLConfig())
                .maxConnections(100)
                .connectionTimeout(Duration.ofSeconds(30))
                .responseTimeout(Duration.ofSeconds(45))
                .build();

            return DockerClientImpl.getInstance(config, dockerHttpClientClient);
        }

        public DockerFileManager build() throws DockerFileManagerException {
            DockerClient dockerClient = createDockerClient();

            return new DockerFileManager(dockerClient, imageId, containerId);
        }
    }
}
