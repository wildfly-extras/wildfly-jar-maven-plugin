/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.wildfly.plugins.bootablejar.maven.goals.BuildBootableJarMojo;

/**
 * @author jdenise
 */
public class Utils {

    public static String getBootableJarPath(String jarFileName, MavenProject project, String goal) throws MojoExecutionException {
        String jarName = jarFileName;
        if (jarName == null) {
            String finalName = project.getBuild().getFinalName();
            jarName = finalName + "-" + BuildBootableJarMojo.BOOTABLE_SUFFIX + "." + BuildBootableJarMojo.JAR;
        }
        String path = project.getBuild().getDirectory() + File.separator + jarName;
        if (!Files.exists(Paths.get(path))) {
            throw new MojoExecutionException("Cannot " + goal + " without a bootable jar; please `mvn wildfly-jar:package` prior to invoking wildfly-jar:run from the command-line");
        }
        return path;
    }

    /**
     * Creates a temporary file in the {@linkplain Build#getDirectory() target} directory.
     *
     * @param project the project to get the target directory for
     * @param paths   the paths to resolve
     *
     * @return the temporary file
     *
     * @throws IOException if the parent paths of the file cannot be created
     */
    public static Path createTemporaryFile(final MavenProject project, final String... paths) throws IOException {
        final Path result = Paths.get(project.getBuild().getDirectory(), paths);
        final Path parent = result.getParent();
        if (parent != null && Files.notExists(parent)) {
            Files.createDirectories(parent);
        }
        return result;
    }

}
