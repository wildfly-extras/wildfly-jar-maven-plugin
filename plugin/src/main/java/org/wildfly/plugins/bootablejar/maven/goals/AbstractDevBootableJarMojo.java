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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.wildfly.core.launcher.BootableJarCommandBuilder;
import org.wildfly.core.launcher.Launcher;
import org.wildfly.plugins.bootablejar.maven.common.Utils;

/**
 * Build and start a bootable JAR for dev-watch mode. This goal monitors the
 * changes in the project and recompile/re-deploy. Type Ctrl-C to kill the
 * running server.
 *
 * @author jfdenise
 */
public abstract class AbstractDevBootableJarMojo extends BuildBootableJarMojo {

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

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().debug(String.format("Skipping run of %s:%s", project.getGroupId(), project.getArtifactId()));
            return;
        }
        configureServer();
        hollowJar = true;
        super.execute();
        doExecute();
    }

    protected abstract void configureServer();

    protected abstract void doExecute() throws MojoExecutionException, MojoFailureException;

    protected Launcher buildLauncher(boolean redirect) throws MojoExecutionException {
        if (isJarPackaging()) {
           BootableJarCommandBuilder builder = BootableJarCommandBuilder.of(Utils.getBootableJarPath(null, project, "dev"))
                    .addJavaOptions(jvmArguments)
                    .addServerArguments(arguments);
            if (redirect) {
                // Always disable color when printing to file.
                builder.addJavaOption(Utils.NO_COLOR_OPTION);
            }
            return Launcher.of(builder);
        } else {
           return server.createServerLauncher(project, jvmArguments, arguments, redirect, "dev");
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
