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
import org.wildfly.core.launcher.BootableJarCommandBuilder;
import org.wildfly.core.launcher.CommandBuilder;
import org.wildfly.core.launcher.Launcher;
import org.wildfly.plugins.bootablejar.maven.common.Utils;

/**
 * Run the bootable JAR. This is blocking.
 *
 * @author jfdenise
 */
@Mojo(name = "run", requiresDependencyResolution = ResolutionScope.RUNTIME)
public final class RunBootableJarMojo extends AbstractMojo {

    /**
     * Additional JVM options.
     */
    @Parameter(property = "wildfly.bootable.jvmArguments")
    public List<String> jvmArguments = new ArrayList<>();

    /**
     * Bootable JAR server arguments.
     */
    @Parameter(property = "wildfly.bootable.arguments")
    public List<String> arguments = new ArrayList<>();

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Set to {@code true} if you want the run goal to be skipped, otherwise
     * {@code false}.
     */
    @Parameter(defaultValue = "false", property = "wildfly.bootable.run.skip")
    private boolean skip;

    /**
     * In case a custom JAR file name was specified during build, set this
     * option to this JAR file name. That is required for the plugin to retrieve
     * the JAR file to run.
     */
    @Parameter(alias = "jar-file-name", property = "wildfly.bootable.run.jar.file.name")
    String jarFileName;

    /**
     * Disable JAR packaging. A directory (named {@code server} by default) containing
     * the server and deployments is created in the project {@code target} directory.
     * In this mode, multiple deployments can be added (in addition to the primary
     * project artifact) by adding ear/war to the {@code external-deployments} configuration element.
     *
     * <br/>
     * &lt;server&gt;<br/>
     * &lt;enabled&gt; {@code true} or {@code false} ({@code true} by default)&lt;/enabled&gt;<br/>
     * &lt;directory-name&gt;name of a directory inside the project target directory ({@code server} by default)&lt;/directory-name&gt;<br/>
     * &lt;/server&gt;<br/>
     */
    @Parameter(alias = "server")
    ServerModeConfig server;

    private boolean isJarPackaging() {
        return server == null || !server.isEnabled();
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().debug(String.format("Skipping run of %s:%s", project.getGroupId(), project.getArtifactId()));
            return;
        }
        try {
            CommandBuilder builder;
            Launcher launcher;
            if (isJarPackaging()) {
                builder = BootableJarCommandBuilder.of(Utils.getBootableJarPath(jarFileName, project, "run"))
                    .addJavaOptions(jvmArguments)
                    .addServerArguments(arguments);
                launcher = Launcher.of(builder);
            } else {
                launcher = server.createServerLauncher(project, jvmArguments, arguments, false, "run");
            }
            final Process process = launcher.inherit().launch();
            process.waitFor();
        } catch (Exception e) {
            throw new MojoExecutionException(e.getLocalizedMessage(), e);
        }
    }

    /**
     * Allows the {@linkplain #jvmArguments} to be set as a string.
     *
     * @param jvmArguments a whitespace delimited string for the JVM arguments
     */
    @SuppressWarnings("unused")
    public void setJvmArguments(final String jvmArguments) {
        this.jvmArguments = Utils.splitArguments(jvmArguments);
    }

    /**
     * Allows the {@linkplain #arguments} to be set as a string.
     *
     * @param arguments a whitespace delimited string for the server arguments
     */
    @SuppressWarnings("unused")
    public void setArguments(final String arguments) {
        this.arguments = Utils.splitArguments(arguments);
    }
}
