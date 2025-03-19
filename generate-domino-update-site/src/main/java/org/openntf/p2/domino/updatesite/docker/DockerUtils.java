package org.openntf.p2.domino.updatesite.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerResponse.ContainerState;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.Image;
import java.io.ByteArrayOutputStream;
import java.io.UnsupportedEncodingException;
import java.util.List;

public class DockerUtils {

	public static ExecResult execCmd(DockerClient dockerClient, String containerId, String... cmdArray) {

		ExecCreateCmdResponse resp = dockerClient.execCreateCmd(containerId).withAttachStderr(true).withAttachStdout(true)
												 .withCmd(cmdArray).exec();

		// Output buffers
		ByteArrayOutputStream stdout = new ByteArrayOutputStream();
		ByteArrayOutputStream stderr = new ByteArrayOutputStream();

		try {
			// Execute command with non-deprecated callback
			dockerClient.execStartCmd(resp.getId()).exec(new ResultCallback.Adapter<Frame>() {
				@Override
				public void onNext(Frame frame) {
					try {
						switch(frame.getStreamType()) {
							case STDOUT:
								stdout.write(frame.getPayload());
								break;
							case STDERR:
								stderr.write(frame.getPayload());
								break;
						}
					} catch(Exception e) {
						throw new RuntimeException(e);
					}
				}
			}).awaitCompletion();

			return new ExecResult(stdout.toString("UTF-8"), stderr.toString("UTF-8"), null);

		} catch(UnsupportedEncodingException uee) {
			// Fallback to default encoding if UTF-8 is not supported
			return new ExecResult(stdout.toString(), stderr.toString(), null);
		} catch(Exception e) {
			return new ExecResult(null, null, e);
		}
	}

	// This method is not using any authentication, so it will not work for private registries
	public static void pullImage(DockerClient dockerClient, String imageId) throws DockerFileManagerException {
		try {
			// If the image does not exist, pull it
			dockerClient
				.pullImageCmd(imageId)
				.exec(new ResultCallback.Adapter<>()) // Ignore the response
				.awaitCompletion();
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt(); // Restore the interrupted status
			throw new DockerFileManagerException("Image pull interrupted", e);
		}
	}

	public static boolean isImageExists(DockerClient dockerClient, String imageId) {
		List<Image> images = dockerClient.listImagesCmd()
										 .withImageNameFilter(imageId)
										 .exec();

		return images != null && !images.isEmpty();
	}

	public static String createContainer(DockerClient dockerClient, String imageId) throws DockerFileManagerException {
		CreateContainerResponse response = dockerClient.createContainerCmd(imageId)
													   .withEntrypoint("/bin/sh")
													   .withTty(true)  // Equivalent to "-t"
													   .withStdinOpen(true) // Equivalent to "-i"
													   .exec();

		// Start the container
		dockerClient.startContainerCmd(response.getId())
					.exec();

		// Check the state
		ContainerState state = dockerClient.inspectContainerCmd(response.getId())
										   .exec()
										   .getState();

		if(Boolean.TRUE.equals(state.getRunning())) {
			return response.getId();
		}

		throw new DockerFileManagerException("Cannot create container with " + imageId);
	}

	public static void stopRemoveContainer(DockerClient dockerClient, String containerId) throws DockerFileManagerException {
		try {
			dockerClient.stopContainerCmd(containerId)
						.withTimeout(3)
						.exec();

			// Wait for the container to stop
			dockerClient.waitContainerCmd(containerId)
						.exec(new ResultCallback.Adapter<>())
						.awaitCompletion();

			dockerClient.removeContainerCmd(containerId)
						.withForce(true)
						.exec();
		} catch(Exception e) {
			throw new DockerFileManagerException("Error stopping container", e);
		}
	}

	public static class ExecResult {

		private final String output;
		private final String error;
		private final Throwable exception;

		ExecResult(String output, String error, Throwable exception) {
			this.output = output;
			this.error = error;
			this.exception = exception;
		}

		public boolean isError() {
			if(exception != null) {
				return true;
			}
			return error != null && !error.isEmpty();
		}

		public boolean isSuccess() {
			return !isError();
		}

		public boolean isInterrupted() {
			return exception instanceof InterruptedException;
		}

		public String getOutput() {
			return output == null ? "" : output;
		}

		public String getError() {
			if(error == null || error.isEmpty()) {
				return exception != null ? exception.getMessage() : "Unknown error";
			}

			return error + (exception != null ? (" (" + exception.getMessage() + ")") : "");
		}
	}

}
