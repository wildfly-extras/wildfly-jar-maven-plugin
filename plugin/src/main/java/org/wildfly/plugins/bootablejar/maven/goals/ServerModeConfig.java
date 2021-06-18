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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.wildfly.core.launcher.CommandBuilder;
import org.wildfly.core.launcher.Launcher;
import org.wildfly.core.launcher.StandaloneCommandBuilder;
import org.wildfly.plugins.bootablejar.maven.common.Utils;

/**
 * Configuration for a server not packaged as a JAR file.
 *
 * @author jdenise
 */
public class ServerModeConfig {

    static class LauncherCommandBuilder implements CommandBuilder {

        private final Path script;
        private final List<String> serverArguments = new ArrayList<>();
        LauncherCommandBuilder(Path script) {
            this.script = script;
        }
        void addServerArguments(List<String> serverArguments) {
            this.serverArguments.addAll(serverArguments);
        }
        @Override
        public List<String> buildArguments() {
            return Collections.emptyList();
        }

        @Override
        public List<String> build() {
            List<String> lst = new ArrayList<>();
            lst.add(script.toString());
            lst.addAll(serverArguments);
            return lst;
        }

    }

    private static final String SERVER_DIRECTORY_NAME = "wildfly.bootable.server.directory.name";
    private static final String SERVER_LAUNCH_SCRIPT = "wildfly.bootable.server.launch.script";
    private static final String DEFAULT_DIRECTORY_NAME = "server";
    /**
     * Allows to name the directory in which the server is created.
     */

    private String directoryName;

    private String launchScript;

    private boolean enabled = true;

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setDirectoryName(String directoryName) {
        this.directoryName = directoryName;
    }

    public String getDirectoryName() {
        String name = System.getProperty(SERVER_DIRECTORY_NAME);
        if (name == null) {
            if (directoryName == null) {
                name = DEFAULT_DIRECTORY_NAME;
            } else {
                name = directoryName;
            }
        }
        return name;
    }

    public void setLaunchScript(String launchScript) {
        this.launchScript = launchScript;
    }

    public String getLaunchScript() {
        String name = System.getProperty(SERVER_LAUNCH_SCRIPT);
        if (name == null) {
            name = launchScript;
        }
        return name;
    }

    public Launcher createServerLauncher(MavenProject project, List<String> jvmArguments, List<String> arguments, boolean redirect, String goal) throws MojoExecutionException {
        String script = getLaunchScript();
        Path jbossHome = Paths.get(Utils.getWildFlyPath(getDirectoryName(), project, goal));
        if (script == null) {
            StandaloneCommandBuilder builder = StandaloneCommandBuilder.of(jbossHome)
                    .addJavaOptions(jvmArguments)
                    .addServerArguments(arguments);
            if (redirect) {
                // Always disable color when printing to file.
                builder.addJavaOption(Utils.NO_COLOR_OPTION);
            }
            return Launcher.of(builder);
        } else {
            LauncherCommandBuilder launchBuilder = new LauncherCommandBuilder(Utils.getWildFlyLauncherPath(getDirectoryName(), project, script, goal));
            launchBuilder.addServerArguments(arguments);
            List<String> jvmArgs = new ArrayList<>();
            jvmArgs.addAll(jvmArguments);
            if (redirect) {
                // Always disable color when printing to file.
                jvmArgs.add(Utils.NO_COLOR_OPTION);
            }
            return Launcher.of(launchBuilder).addEnvironmentVariable("JBOSS_HOME", jbossHome.toString()).
                    addEnvironmentVariable("JAVA_OPTS", String.join(" ", jvmArgs));
        }
    }
}
