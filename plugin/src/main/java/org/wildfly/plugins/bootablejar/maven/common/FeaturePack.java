/*
 * Copyright 2016-2020 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.plugins.bootablejar.maven.common;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.apache.maven.shared.artifact.ArtifactCoordinate;
import org.apache.maven.shared.dependencies.DependableCoordinate;
import org.jboss.galleon.util.StringUtils;

/**
 * Copied from Galleon plugin and simplified to cope with bootable JAR.
 *
 * @author JF Denise
 * @author Alexey Loubyanssky
 */
public class FeaturePack implements DependableCoordinate, ArtifactCoordinate {

    private String groupId;
    private String artifactId;
    private String version;
    private String type = "zip";
    private String classifier;
    private String extension = "zip";

    private String location;

    private String includedDefaultConfig;

    private Boolean inheritPackages = false;
    private List<String> excludedPackages = Collections.emptyList();
    private List<String> includedPackages = Collections.emptyList();

    private Path path;

    @Override
    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        assertGalleon1Location();
        this.groupId = groupId;
    }

    @Override
    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        assertGalleon1Location();
        this.artifactId = artifactId;
    }

    @Override
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        assertGalleon1Location();
        this.version = version;
    }

    @Override
    public String getType() {
        return type;
    }

    public void setType(String type) {
        assertGalleon1Location();
        this.type = type;
    }

    @Override
    public String getClassifier() {
        return classifier;
    }

    public void setClassifier(String classifier) {
        assertGalleon1Location();
        this.classifier = classifier;
    }

    @Override
    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        assertGalleon1Location();
        this.extension = extension;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        assertGalleon2Location();
        this.location = location;
    }

    public Boolean isInheritPackages() {
        return inheritPackages;
    }

    public void setInheritPackages(boolean inheritPackages) {
        this.inheritPackages = inheritPackages;
    }

    public String getIncludedDefaultConfig() {
        return includedDefaultConfig;
    }

    public void setIncludedConfigs(String includedDefaultConfig) {
        this.includedDefaultConfig = includedDefaultConfig;
    }

    public List<String> getExcludedPackages() {
        return excludedPackages;
    }

    public void setExcludedPackages(List<String> excludedPackages) {
        this.excludedPackages = excludedPackages;
    }

    public List<String> getIncludedPackages() {
        return includedPackages;
    }

    public void setIncludedPackages(List<String> includedPackages) {
        this.includedPackages = includedPackages;
    }

    public void setPath(File path) {
        assertPathLocation();
        this.path = path.toPath().normalize();
    }

    public Path getNormalizedPath() {
        return path;
    }

    public String getMavenCoords() {
        StringBuilder builder = new StringBuilder();
        builder.append(getGroupId()).append(":").append(getArtifactId());
        String type = getExtension() == null ? getType() : getExtension();
        if (getClassifier() != null || type != null) {
            builder.append(":").append(getClassifier() == null ? "" : getClassifier()).append(":").append(type == null ? "" : type);
        }
        if (getVersion() != null) {
            builder.append(":").append(getVersion());
        }
        return builder.toString();
    }

    public String getGAC() {
        StringBuilder builder = new StringBuilder();
        builder.append(getGroupId()).append(":").append(getArtifactId());
        String type = getExtension() == null ? getType() : getExtension();
        if (getClassifier() != null) {
            builder.append(":").append(getClassifier());
        }
        return builder.toString();
    }
    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append('{');
        if (location != null) {
            buf.append(location);
        } else {
            buf.append(groupId).append(':').append(artifactId).append(':').append(version);
        }
        buf.append(" inherit-packages=").append(inheritPackages);
        if (!includedPackages.isEmpty()) {
            buf.append(" included-packages=");
            StringUtils.appendList(buf, includedPackages);
        }
        if (!excludedPackages.isEmpty()) {
            buf.append(" excluded-packages=");
            StringUtils.appendList(buf, excludedPackages);
        }
        if (includedDefaultConfig != null) {
            buf.append(" included-default-config=");
            buf.append(includedDefaultConfig);
        }
        return buf.append('}').toString();
    }

    private void assertPathLocation() {
        if (groupId != null || artifactId != null || version != null) {
            throw new IllegalStateException("feature-pack Path cannot be used: Galleon 1.x feature-pack Maven coordinates have already been initialized");
        }
        if (location != null) {
            throw new IllegalStateException("feature-pack Path cannot be used: Galleon 2.x location has already been initialized");
        }
    }

    private void assertGalleon2Location() {
        if (groupId != null || artifactId != null || version != null) {
            throw new IllegalStateException("Galleon 2.x location cannot be used: feature-pack Maven coordinates have already been initialized");
        }
        if (path != null) {
            throw new IllegalStateException("Galleon 2.x location cannot be used: feature-pack Path has already been initialized");
        }
    }

    private void assertGalleon1Location() {
        if (location != null) {
            throw new IllegalStateException("Galleon 1.x feature-pack Maven coordinates cannot be used: Galleon 2.x feature-pack location has already been initialized");
        }
        if (path != null) {
            throw new IllegalStateException("Galleon 1.x feature-pack Maven coordinates cannot be used: feature-pack Path has already been initialized");
        }
    }
}
