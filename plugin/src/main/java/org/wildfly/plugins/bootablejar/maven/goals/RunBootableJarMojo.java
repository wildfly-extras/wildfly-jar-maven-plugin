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
package org.wildfly.plugins.bootablejar.maven.goals;

import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.wildfly.plugins.bootablejar.maven.common.Utils;

/**
 * Run the bootable jar. This is blocking.
 *
 * @author jfdenise
 */
@Mojo(name = "run", requiresDependencyResolution = ResolutionScope.RUNTIME)
public final class RunBootableJarMojo extends AbstractMojo {

    /**
     * Additional JVM options.
     */
    @Parameter(alias = "jvmArguments", property = "wildfly.bootable.run.jvmArguments")
    public List<String> jvmArguments = new ArrayList<>();

    /**
     * Bootable jar arguments.
     */
    @Parameter(alias = "arguments", property = "wildfly.bootable.run.arguments")
    public List<String> arguments = new ArrayList<>();

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Set to {@code true} if you want the deployment to be skipped, otherwise
     * {@code false}.
     */
    @Parameter(defaultValue = "false", property = "wildfly.bootable.run.skip")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().debug(String.format("Skipping run of %s:%s", project.getGroupId(), project.getArtifactId()));
            return;
        }
        Utils.startBootableJar(Utils.getBootableJarPath(project, "run"), jvmArguments, arguments, true,
                false, null, -1);
    }
}
