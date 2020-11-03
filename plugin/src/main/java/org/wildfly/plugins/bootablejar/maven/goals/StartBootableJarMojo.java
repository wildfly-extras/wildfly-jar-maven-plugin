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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.jboss.as.controller.client.ModelControllerClient;
import org.wildfly.core.launcher.BootableJarCommandBuilder;
import org.wildfly.core.launcher.Launcher;
import org.wildfly.plugin.common.AbstractServerConnection;
import org.wildfly.plugin.core.ServerHelper;
import org.wildfly.plugins.bootablejar.maven.common.Utils;

/**
 * Start the bootable JAR. The plugin execution keeps the process running.
 *
 * @author jfdenise
 */
@Mojo(name = "start", requiresDependencyResolution = ResolutionScope.RUNTIME)
public class StartBootableJarMojo extends AbstractServerConnection {

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
     * Set to {@code true} if you want the start goal to be skipped, otherwise
     * {@code false}.
     */
    @Parameter(defaultValue = "false", property = "wildfly.bootable.start.skip")
    private boolean skip;

    /**
     * Set to {@code false} if you don't want the plugin to check for server
     * status before to return. In case the started server has no management
     * interface enabled this parameter should be set to true.
     */
    @Parameter(alias = "check-server-start", defaultValue = "true", property = "wildfly.bootable.start.check.start")
    private boolean checkStarted;

    /**
     * The timeout value to use when checking for the server to be running.
     */
    @Parameter(alias = "startup-timeout", defaultValue = "60", property = "wildfly.bootable.start.timeout")
    private long startupTimeout;

    /**
     * The Bootable JAR Process id.
     */
    @Parameter(alias = "id", defaultValue = "60", property = "wildfly.bootable.start.id")
    private String id;

    /**
     * In case a custom JAR file name was specified during build, set this
     * option to this JAR file name. That is required for the plugin to retrieve
     * the JAR file to start.
     */
    @Parameter(alias = "jar-file-name", property = "wildfly.bootable.start.jar.file.name")
    String jarFileName;

    /**
     * Indicates how {@code stdout} and {@code stderr} should be handled for the server process. A value of
     * {@code inherit} means that the standard output streams are inherited from the current process. Any other value is
     * assumed to be a path. In this case both {@code stdout} and {@code stderr} will be redirected to a file.
     */
    @Parameter(defaultValue = "${project.build.directory}/wildfly-jar-start-stdout.log", property = "wildfly.bootable.stdout")
    public String stdout;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        doExecute(project);
    }

    private void doExecute(MavenProject project) throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().debug(String.format("Skipping " + goal() + " of %s:%s", project.getGroupId(), project.getArtifactId()));
            return;
        }

        final BootableJarCommandBuilder commandBuilder = BootableJarCommandBuilder.of(Utils.getBootableJarPath(jarFileName, project, goal()))
                .addJavaOptions(jvmArguments)
                .addServerArguments(arguments);
        try {
            final Launcher launcher = Launcher.of(commandBuilder);

            if ("inherit".equalsIgnoreCase(stdout)) {
                launcher.inherit();
            } else {
                final Path redirect = Paths.get(stdout);
                getLog().info(String.format("The stdout and stderr for the process are being logged to %s", redirect));
                launcher.setRedirectErrorStream(true)
                        .redirectOutput(redirect);
            }
            final Process process = launcher.launch();
            if (checkStarted) {
                try (ModelControllerClient client = createClient()) {
                    ServerHelper.waitForStandalone(process, client, startupTimeout);
                }
            }
        } catch (Exception e) {
            throw new MojoExecutionException(e.getLocalizedMessage(), e);
        }
    }

    @Override
    public String goal() {
        return "start";
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
