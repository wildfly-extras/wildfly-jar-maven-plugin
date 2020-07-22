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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.jboss.as.controller.client.ModelControllerClient;
import org.wildfly.plugin.common.AbstractServerConnection;
import org.wildfly.plugins.bootablejar.maven.common.Utils;

/**
 * Start the bootable jar. The plugin execution keeps the process running.
 *
 * @author jfdenise
 */
@Mojo(name = "start", requiresDependencyResolution = ResolutionScope.RUNTIME)
public class StartBootableJarMojo extends AbstractServerConnection {

    /**
     * Additional JVM options.
     */
    @Parameter(alias = "jvmArguments")
    public List<String> jvmArguments = new ArrayList<>();

    /**
     * Bootable jar server arguments.
     */
    @Parameter(alias = "arguments")
    public List<String> arguments = new ArrayList<>();

    /**
     * Additional JVM options that can be set thanks to system property.
     */
    @Parameter(property = "wildfly.bootable.jvmArguments")
    public String jvmArgumentsProps;

    /**
     * Bootable jar server arguments that can be set thanks to system property.
     */
    @Parameter(property = "wildfly.bootable.arguments")
    public String argumentsProps;


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
     * The Bootable Jar Process id.
     */
    @Parameter(alias = "id", defaultValue = "60", property = "wildfly.bootable.start.id")
    private String id;

    /**
     * In case a custom jar file name was specified during build, set this option
     * to this jar file name. That is required for the plugin to retrieve the jar file to start.
     */
    @Parameter(alias = "jar-file-name", property = "wildfly.bootable.start.jar.file.name")
    String jarFileName;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        doExecute(project);
    }

    private void doExecute(MavenProject project) throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().debug(String.format("Skipping " + goal() + " of %s:%s", project.getGroupId(), project.getArtifactId()));
            return;
        }
        ModelControllerClient client = null;
        if (checkStarted) {
            client = createClient();
        }

        if (jvmArgumentsProps != null) {
            StringTokenizer args = new StringTokenizer(jvmArgumentsProps);
            while (args.hasMoreTokens()) {
                this.jvmArguments.add(args.nextToken());
            }
        }

        if (argumentsProps != null) {
            StringTokenizer args = new StringTokenizer(argumentsProps);
            while (args.hasMoreTokens()) {
                this.arguments.add(args.nextToken());
            }
        }

        try {
            Utils.startBootableJar(Utils.getBootableJarPath(jarFileName, project, goal()), jvmArguments, arguments, false,
                    checkStarted, client, startupTimeout);
        } finally {
            if (client != null) {
                try {
                    client.close();
                } catch (IOException ex) {
                    throw new MojoExecutionException(ex.getMessage(), ex);
                }
            }
        }
    }

    void startDevMode(MavenProject project) throws MojoExecutionException, MojoFailureException {
        doExecute(project);
    }

    @Override
    public String goal() {
        return "start";
    }
}
