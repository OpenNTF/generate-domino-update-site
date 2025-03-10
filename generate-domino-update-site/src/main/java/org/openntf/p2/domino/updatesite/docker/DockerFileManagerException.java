package org.openntf.p2.domino.updatesite.docker;

public class DockerFileManagerException extends Exception {

    private static final long serialVersionUID = 1L;

    public DockerFileManagerException(String message) {
        super(message);
    }

    public DockerFileManagerException(String message, Throwable cause) {
        super(message, cause);
    }

    public DockerFileManagerException(Throwable cause) {
        super(cause);
    }

}
