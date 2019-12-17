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
package org.wildfly.galleon.maven;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

/**
 * Run the uberjar
 *
 * @author jfdenise
 */
@Mojo(name = "run", requiresDependencyResolution = ResolutionScope.RUNTIME)
public final class RunUberJarMojo extends AbstractMojo {

    /**
     * Additional JVM options.
     */
    @Parameter(alias = "jvmArguments", property = "wildfly.uberjar.run.jvmArguments")
    public List<String> jvmArguments = new ArrayList<>();

    /**
     * Uberjar arguments.
     */
    @Parameter(alias = "arguments", property = "wildfly.uberjar.run.arguments")
    public List<String> arguments = new ArrayList<>();

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Set to {@code true} if you want the deployment to be skipped, otherwise
     * {@code false}.
     */
    @Parameter(defaultValue = "false", property = "wildfly.uberjar.run.skip")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().debug(String.format("Skipping run of %s:%s", project.getGroupId(), project.getArtifactId()));
            return;
        }
        String finalName = this.project.getBuild().getFinalName();
        String jarName = finalName + "-" + BuildUberJarMojo.UBERJAR_SUFFIX + "." + BuildUberJarMojo.JAR;
        String path = this.project.getBuild().getDirectory() + File.separator + jarName;
        if (!Files.exists(Paths.get(path))) {
            throw new MojoExecutionException("Cannot run without an uberjar; please `mvn wildfly-uberjar:package` prior to invoking wildfly-uberjar:run from the command-line");
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(getJava());
        cmd.addAll(jvmArguments);
        cmd.add("-jar");
        cmd.add(path);
        cmd.addAll(arguments);
        ProcessBuilder builder = new ProcessBuilder(cmd).inheritIO();
        try {
            Process p = builder.start();
            p.waitFor();
        } catch (Exception ex) {
            throw new MojoExecutionException(ex.getLocalizedMessage(), ex);
        }
    }

    private static String getJava() {
        String exe = "java";
        if (isWindows()) {
            exe = "java.exe";
        }
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome == null) {
            return exe;
        } else {
            return javaHome + File.separator + "bin" + File.separator + exe;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", null).toLowerCase(Locale.ENGLISH).contains("windows");
    }
}
