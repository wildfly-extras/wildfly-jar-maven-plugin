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
package org.wildfly.uberjar.maven.common;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.jboss.as.controller.client.ModelControllerClient;
import org.wildfly.plugin.core.ServerHelper;
import org.wildfly.uberjar.maven.goals.BuildUberJarMojo;

/**
 *
 * @author jdenise
 */
public class Utils {

    public static String getUberJarPath(MavenProject project, String goal) throws MojoExecutionException {
        String finalName = project.getBuild().getFinalName();
        String jarName = finalName + "-" + BuildUberJarMojo.UBERJAR_SUFFIX + "." + BuildUberJarMojo.JAR;
        String path = project.getBuild().getDirectory() + File.separator + jarName;
        if (!Files.exists(Paths.get(path))) {
            throw new MojoExecutionException("Cannot " + goal + " without an uberjar; please `mvn wildfly-uberjar:package` prior to invoking wildfly-uberjar:run from the command-line");
        }
        return path;
    }

    public static void startUberJar(String jarPath, List<String> jvmArguments,
            List<String> arguments, boolean waitFor,
            boolean checkStart,
            ModelControllerClient client, long timeout) throws MojoExecutionException {
        List<String> cmd = new ArrayList<>();
        cmd.add(getJava());
        cmd.addAll(jvmArguments);
        cmd.add("-jar");
        cmd.add(jarPath);
        cmd.addAll(arguments);
        ProcessBuilder builder = new ProcessBuilder(cmd).inheritIO();
        try {
            Process p = builder.start();
            if (waitFor) {
                p.waitFor();
            } else {
                if (checkStart) {
                    checkStarted(client, timeout);
                }
            }
        } catch (Exception ex) {
            throw new MojoExecutionException(ex.getLocalizedMessage(), ex);
        }
    }

    private static void checkStarted(ModelControllerClient client, long timeout) throws Exception {
        ServerHelper.waitForStandalone(null, client, timeout);
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
