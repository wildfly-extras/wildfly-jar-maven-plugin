/*
 * Copyright 2016-2019 Red Hat, Inc. and/or its affiliates
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
package org.wildfly.uberjar.maven.goals;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.jboss.as.controller.client.ModelControllerClient;
import org.wildfly.plugin.common.AbstractServerConnection;
import org.wildfly.uberjar.maven.common.Utils;

/**
 * Start the uberjar. The plugin execution keeps the process running.
 *
 * @author jfdenise
 */
@Mojo(name = "start", requiresDependencyResolution = ResolutionScope.RUNTIME)
public class StartUberJarMojo extends AbstractServerConnection {

    /**
     * Additional JVM options.
     */
    @Parameter(alias = "jvmArguments", property = "wildfly.uberjar.start.jvmArguments")
    public List<String> jvmArguments = new ArrayList<>();

    /**
     * Uberjar arguments.
     */
    @Parameter(alias = "arguments", property = "wildfly.uberjar.start.arguments")
    public List<String> arguments = new ArrayList<>();

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Set to {@code true} if you want the deployment to be skipped, otherwise
     * {@code false}.
     */
    @Parameter(defaultValue = "false", property = "wildfly.uberjar.start.skip")
    private boolean skip;

    /**
     * Set to {@code false} if you don't want the plugin to check for server
     * status before to return. In case the started server has no management
     * interface enabled this parameter should be set to true.
     */
    @Parameter(alias = "check-server-start", defaultValue = "true", property = "wildfly.uberjar.start.check.start")
    private boolean checkStarted;

    /**
     * The timeout value to use when checking for the server to be running.
     */
    @Parameter(alias = "startup-timeout", defaultValue = "60", property = "wildfly.uberjar.start.timeout")
    private long startupTimeout;

    /**
     * The Uberjar Process id.
     */
    @Parameter(alias = "id", defaultValue = "60", property = "wildfly.uberjar.start.id")
    private String id;


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().debug(String.format("Skipping " + goal() + " of %s:%s", project.getGroupId(), project.getArtifactId()));
            return;
        }
        ModelControllerClient client = null;
        if (checkStarted) {
            client = createClient();
        }
        try {
            Utils.startUberJar(Utils.getUberJarPath(project, goal()), jvmArguments, arguments, false,
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

    @Override
    public String goal() {
        return "start";
    }
}
