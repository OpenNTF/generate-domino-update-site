package org.openntf.p2.domino.updatesite.model;

import java.text.MessageFormat;
import java.util.List;

import lombok.Data;

@Data
public class BundleInfo {
	private final String name;
	private final String vendor;
	private final String artifactId;
	private final String version;
	private final String filePath;
	private final List<String> requires;
	private final List<BundleEmbed> embeds;
	
	public BundleInfo(String name, String vendor, String artifactId, String version, String filePath, List<String> requires, List<BundleEmbed> embeds) {
		this.name = name;
		this.vendor = vendor;
		this.artifactId = artifactId;
		this.version = version;
		this.filePath = filePath;
		this.requires = requires;
		this.embeds = embeds;
	}
	
	@Override
	public String toString() {
		return MessageFormat.format("[{0}: name={1}, vendor={2}, artifactId={3}, version={4}, filePath={5}, requires={6}, embeds={7}]", //$NON-NLS-1$
				getClass().getSimpleName(),
				name,
				vendor,
				artifactId,
				version,
				filePath,
				requires,
				embeds
		);
	}
}