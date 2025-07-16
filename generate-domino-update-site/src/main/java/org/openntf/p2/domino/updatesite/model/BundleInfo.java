/**
 * Copyright © 2018-2025 Contributors to the generate-domino-update-site project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openntf.p2.domino.updatesite.model;

import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.List;
import java.util.Objects;

public class BundleInfo {

    private final String name;
    private final String vendor;
    private final String artifactId;
    private final String version;
    private final String filePath;
    private final List<String> requires;
    private final List<BundleEmbed> embeds;
    private final Path source;

    public BundleInfo(String name, String vendor, String artifactId, String version, String filePath, List<String> requires,
        List<BundleEmbed> embeds, Path source) {
        this.name = name;
        this.vendor = vendor;
        this.artifactId = artifactId;
        this.version = version;
        this.filePath = filePath;
        this.requires = requires;
        this.embeds = embeds;
        this.source = source;
    }

    public String getName() {
        return name;
    }

    public String getVendor() {
        return vendor;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public String getFilePath() {
        return filePath;
    }

    public List<String> getRequires() {
        return requires;
    }

    public List<BundleEmbed> getEmbeds() {
        return embeds;
    }

    public Path getSource() {
        return source;
    }

    @Override
    public String toString() {
        return MessageFormat.format("[{0}: name={1}, vendor={2}, artifactId={3}, version={4}, filePath={5}, requires={6}, embeds={7}, source={8}]", //$NON-NLS-1$
                                    getClass().getSimpleName(),
                                    name,
                                    vendor,
                                    artifactId,
                                    version,
                                    filePath,
                                    requires,
                                    embeds,
                                    source
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, vendor, artifactId, version, filePath, requires, embeds, source);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BundleInfo)) {
            return false;
        }
        BundleInfo that = (BundleInfo) o;
        return Objects.equals(name, that.name) &&
            Objects.equals(vendor, that.vendor) &&
            Objects.equals(artifactId, that.artifactId) &&
            Objects.equals(version, that.version) &&
            Objects.equals(filePath, that.filePath) &&
            Objects.equals(requires, that.requires) &&
            Objects.equals(embeds, that.embeds) &&
            Objects.equals(source, that.source);
    }
}
