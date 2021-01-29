/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates
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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.shared.dependencies.DependableCoordinate;

public class OverriddenArtifact implements DependableCoordinate {

    private String groupId;
    private String artifactId;
    private String version;
    private String type = "jar";
    private String classifier;

    @Override
    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    @Override
    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    @Override
    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    @Override
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String getClassifier() {
        return classifier;
    }

    public void setClassifier(String classifier) {
        this.classifier = classifier;
    }

    public String getGAC() {
        return getGroupId() + ":" + getArtifactId() + (getClassifier() == null ? "" : ":" + getClassifier());
    }

    //grpid:artifactId:version:[classifier]:extension
    public static OverriddenArtifact fromString(String str) throws MojoExecutionException {
        if (str == null) {
            throw new MojoExecutionException("Invalid null overriden artifact");
        }
        str = str.trim();

        String[] parts = str.split(":");
        if (parts.length < 2) {
            throw new MojoExecutionException("Unexpected artifact coordinates format: " + str);
        }
        OverriddenArtifact artifact = new OverriddenArtifact();
        artifact.setGroupId(check(parts[0], str));
        artifact.setArtifactId(check(parts[1], str));
        if (parts.length > 2) {
            String version = parts[2].trim();
            if (!version.isEmpty()) {
                artifact.setVersion(version);
            }
            if (parts.length > 3) {
                String classifier = parts[3].trim();
                artifact.setClassifier(classifier);
                if (parts.length > 4) {
                    String type = parts[4].trim();
                    if (!type.isEmpty()) {
                        artifact.setType(type);
                    }
                    if (parts.length > 5) {
                        throw new MojoExecutionException("Unexpected artifact coordinates format: " + str);
                    }
                }
            }
        }
        return artifact;
    }

    private static String check(String artifact, String item) {
        if (item != null) {
            item = item.trim();
        }
        if (item == null || item.isEmpty()) {
            throw new IllegalArgumentException("Unexpected artifact coordinates format: " + artifact);
        }
        return item;
    }
}
