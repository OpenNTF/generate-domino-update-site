package org.openntf.p2.domino.updatesite.model;

import java.nio.file.Path;
import java.text.MessageFormat;

import lombok.Data;

@Data
public class BundleEmbed {
	private final String name;
	private final Path file;
	
	public BundleEmbed(String name, Path file) {
		this.name = name;
		this.file = file;
	}
	
	@Override
	public String toString() {
		return MessageFormat.format("[{0}: name={1}]", getClass().getSimpleName(), name); //$NON-NLS-1$
	}
}