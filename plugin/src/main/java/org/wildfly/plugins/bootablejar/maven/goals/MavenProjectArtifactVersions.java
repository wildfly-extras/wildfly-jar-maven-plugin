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
package org.wildfly.plugins.bootablejar.maven.goals;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.wildfly.plugins.bootablejar.maven.common.ExternalDeploymentArtifact;
import org.wildfly.plugins.bootablejar.maven.common.OverriddenArtifact;

final class MavenProjectArtifactVersions {

    private static final String TEST_JAR = "test-jar";
    private static final String SYSTEM = "system";

    static MavenProjectArtifactVersions getInstance(MavenProject project) {
        return new MavenProjectArtifactVersions(project);
    }

    private final Map<String, Artifact> artifactVersions = new TreeMap<>();
    private final Map<String, Artifact> fpVersions = new TreeMap<>();

    private final Set<Artifact> deployments = new HashSet<>();

    private MavenProjectArtifactVersions(MavenProject project) {
        for (Artifact artifact : project.getArtifacts()) {
            if (TEST_JAR.equals(artifact.getType()) || SYSTEM.equals(artifact.getScope())) {
                continue;
            }
            if ( ( artifact.getType().equalsIgnoreCase("ejb") || artifact.getType().equalsIgnoreCase("jar") ||
                    artifact.getType().equalsIgnoreCase("war") || artifact.getType().equalsIgnoreCase("ear") ) && !artifact.getScope().equalsIgnoreCase("provided")) {
                deployments.add(artifact);
            }
            put(artifact);
        }
    }

    public Artifact getDeployment(ExternalDeploymentArtifact externalDeployment) throws MojoExecutionException {
        for (Artifact a : deployments) {
            if (externalDeployment.getGroupId() == null || externalDeployment.getArtifactId() == null) {
                throw new MojoExecutionException("External deployment must have non null groupId and artifactId");
            }
            if (externalDeployment.getGroupId().equals(a.getGroupId()) && externalDeployment.getArtifactId().equals(a.getArtifactId())
                    && (externalDeployment.getClassifier() == null || externalDeployment.getClassifier().equals(a.getClassifier()))) {
                return a;
            }
        }
        throw new MojoExecutionException("Deployment artifact not found in dependencies for " + externalDeployment.getGroupId() + ":" +
                externalDeployment.getArtifactId());
    }

    public Artifact getArtifact(OverriddenArtifact artifact) {
        String key = getKey(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier());
        Artifact a = artifactVersions.get(key);
        return a;
    }

    public Artifact getFeaturePackArtifact(String groupId, String artifactId, String classifier) {
        String key = getKey(groupId, artifactId, classifier);
        return fpVersions.get(key);
    }

    private static String getKey(String groupId, String artifactId, String classifier) {
        StringBuilder buf = new StringBuilder(groupId).append(':').
                append(artifactId);
        if (classifier != null && !classifier.isEmpty()) {
            buf.append("::").append(classifier);
        }
        return buf.toString();
    }

    private void put(Artifact artifact) {
        String key = getKey(artifact.getGroupId(), artifact.getArtifactId(), artifact.getClassifier());
        if ("zip".equals(artifact.getType())) {
            fpVersions.put(key, artifact);
        } else {
            artifactVersions.put(key, artifact);
        }
    }
}
