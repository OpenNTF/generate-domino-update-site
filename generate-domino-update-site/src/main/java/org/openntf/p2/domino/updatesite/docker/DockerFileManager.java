package org.openntf.p2.domino.updatesite.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.openntf.p2.domino.updatesite.docker.DockerUtils.ExecResult;

public class DockerFileManager implements AutoCloseable {

    private final String containerId;
    private final boolean removeContainerAfterUse;
    private final boolean keepFilesAfterUse;
    private final Path targetPath;
    private final DockerClient dockerClient;

    /**
     * Constructor for DockerFileManager. Use builder pattern to create an instance.
     * @param dockerClient DockerClient is created using the builder pattern.
     * @param imageId the image ID to create a container from. If null, the containerId must be provided.
     * @param containerId the container ID to use. If null, a new container will be created from the imageId.
     * @param keepFilesAfterUse whether to keep temporary files after use. If true, the temporary directory will not be deleted.
     * @throws DockerFileManagerException if there is an error creating the DockerFileManager
     */
    private DockerFileManager(DockerClient dockerClient, String imageId, String containerId, boolean keepFilesAfterUse) throws DockerFileManagerException {
        this.dockerClient = dockerClient;
        this.keepFilesAfterUse = keepFilesAfterUse;

        if (StringUtils.isAllEmpty(imageId, containerId)) {
            throw new IllegalArgumentException("Either imageId or containerId must be provided.");
        }

        if (StringUtils.isNotEmpty(imageId)) {
            if(! DockerUtils.isImageExists(dockerClient, imageId)) {
                // If the image does not exist, pull it
                DockerUtils.pullImage(dockerClient, imageId);
            }

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

        if(!keepFilesAfterUse) {
            // Delete the temporary directory and its contents
            try(Stream<Path> paths = Files.walk(targetPath)) {
                paths.sorted(Comparator.reverseOrder())
                     .forEach(path -> {
                         try {
                             Files.delete(path);
                         } catch(IOException e) {
                             System.err.println("Unable to delete file: " + path + " - " + e.getMessage());
                         }
                     });
            }
        }
    }

    /**
     * Download a single file from the container. The file will be downloaded to a temporary directory.
     * @param filePath path to the file inside the container
     * @return the path to the downloaded file
     * @throws DockerFileManagerException if there is an error executing the command
     */
    public Path downloadFile(String filePath) throws DockerFileManagerException {
        if(fileExists(filePath)) {
            Path remotePath = Paths.get(filePath);
            Path localPath = downloadFileResources(Collections.singletonList(remotePath), false);

            return localPath.resolve(remotePath.getFileName().toString());
        } else {
            throw new DockerFileManagerException("File does not exist: " + filePath);
        }
    }

    /**
     * Download a directory from the container. The directory will be downloaded as a tar file. It can be extracted as well.
     * @param directoryPath path to the directory inside the container
     * @param extract if true, the tar file will be extracted to a temporary directory and deleted.
     * @return the path to the downloaded tar file or the directory containing the extracted files
     * @throws DockerFileManagerException if there is an error executing the command
     */
    public Path downloadDirectory(String directoryPath, boolean extract) throws DockerFileManagerException {
        if (directoryExists(directoryPath)) {
            Path remotePath = Paths.get(directoryPath);
            Path localPath = downloadFileResources(Collections.singletonList(remotePath), extract);

            if(extract) {
                // If the directory was extracted, return the path to the extracted directory
                return localPath;
            } else {
                // If the directory was not extracted, return the path to the tar file
                return localPath.resolve(remotePath.getFileName().toString() + ".tar");
            }

        } else {
            throw new DockerFileManagerException("Directory does not exist: " + directoryPath);
        }
    }

    /**
     * Download a list of files or directories from the container. All resources will be downloaded to a temporary directory.
     * The directories will be downloaded as tar files. If extractDirectories is true, the tar files will be extracted.
     * @param paths list of paths to the files or directories inside the container
     * @param extractDirectories if true, the tar files will be extracted and deleted.
     * @return the path to the temporary directory containing the downloaded files
     * @throws DockerFileManagerException if there is an error executing the command
     */
    public Path downloadFileResources(List<Path> paths, boolean extractDirectories) throws DockerFileManagerException {
        if (paths == null) {
            throw new IllegalArgumentException("Paths cannot be null or empty");
        }

        try {
            // Create a temporary directory to store the downloaded file
            Path tempPath = Files.createTempDirectory(targetPath, "fd");

            for(Path path: paths) {
                downloadFileResource(path, tempPath, extractDirectories);
            }

            return tempPath;
        } catch (IOException e) {
            throw new DockerFileManagerException("Unable to copy directory", e);
        }
    }

    /**
     * Download a file or directory from the container. The directory will be downloaded as a tar file and tar file migh be extracted.
     *
     * @param remotePath path to the file or directory inside the container
     * @param localPath path to the local directory where the file or directory will be downloaded
     * @param extractIfDirectory if true, the tar file will be extracted and deleted.
     * @throws DockerFileManagerException if there is an error executing the command
     */
    public void downloadFileResource(Path remotePath, Path localPath, boolean extractIfDirectory) throws DockerFileManagerException {
        Path targetPath;
        boolean isDirectory = false;

        try {
            if(fileExists(remotePath.toString())) {
                targetPath = localPath.resolve(remotePath.getFileName().toString());
            } else if(directoryExists(remotePath.toString())) {
                // Directories are downloaded as tar files
                targetPath = localPath.resolve(remotePath.getFileName().toString() + ".tar");
                isDirectory = true;
            } else {
                throw new DockerFileManagerException("Resource does not exist: " + remotePath);
            }

            // Download file or directory
            InputStream is = dockerClient.copyArchiveFromContainerCmd(containerId, remotePath.toString()).exec();
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);

            if(isDirectory && extractIfDirectory) {
                // Extract the zip file
                extractTarFile(targetPath, localPath);

                // Delete the tar file
                if(! targetPath.toFile().delete()) {
                    System.err.println("Unable to delete tar file: " + targetPath);
                }
            }

        } catch (IOException e) {
            throw new DockerFileManagerException("Unable to copy resource: " + remotePath, e);
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

    // Extracts uncompressed tar file
    public static void extractTarFile(Path tarFile, Path outputDir) throws IOException {
        // Ensure output directory exists
        Files.createDirectories(outputDir);

        try(InputStream fileInput = Files.newInputStream(tarFile);
            TarArchiveInputStream tarInput = new TarArchiveInputStream(fileInput)) {

            TarArchiveEntry entry;
            while((entry = tarInput.getNextEntry()) != null) {
                Path outputPath = outputDir.resolve(entry.getName());

                if(entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                } else {
                    Files.createDirectories(outputPath.getParent()); // Ensure parent directory exists

                    try(OutputStream os = Files.newOutputStream(outputPath);
                        BufferedOutputStream bufferedOutput = new BufferedOutputStream(os)) {
                        IOUtils.copy(tarInput, bufferedOutput);
                    }
                }
            }
        }
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {

        private String containerId;
        private String imageId;
        private boolean keepFilesAfterUse = false;

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

        /**
         * Set whether to keep temporary files after use. If true, the temporary directory will not be deleted.
         * Use this only for testing!
         * @return this builder
         */
        public Builder keepFilesAfterUse() {
            this.keepFilesAfterUse = true;
            return this;
        }

        private DockerClient createDockerClient() {
            // Docker client configuration
            DefaultDockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                                                                        //.withDockerContext("context-name")
                                                                        //.withDockerHost("tcp://docker-host:2375")
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

            return new DockerFileManager(dockerClient, imageId, containerId, keepFilesAfterUse);
        }
    }
}
