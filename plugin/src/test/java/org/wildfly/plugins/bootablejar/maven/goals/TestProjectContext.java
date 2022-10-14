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
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.ProjectBuildingException;

/**
 *
 * @author jdenise
 */
class TestProjectContext implements DevWatchContext.ProjectContext {

    private final Path projectBuildDir;
    private final boolean contextRoot;
    private final List<CliSession> cliSessions;
    private final List<String> extraServerContent;
    private final Path javaDir;
    private final Path baseDir;
    private final Path deploymentsDir;
    private final Set<Path> resources;
    private final String finalName;
    private final String packaging;
    boolean pluginConfigUpdated;

    boolean compileCalled;
    boolean packageJarCalled;
    boolean packageWarCalled;
    boolean checkPluginCalled;
    boolean cleanupCalled;
    boolean resourcesCalled;

    TestProjectContext(Path baseDir, String finalName, String packaging,
            Path deploymentsDir,
            Path projectBuildDir,
            Path javaDir,
            boolean contextRoot,
            List<CliSession> cliSessions,
            List<String> extraServerContent,
            Set<Path> resources,
            boolean pluginConfigUpdated) throws IOException {
        this.baseDir = baseDir;
        this.finalName = finalName;
        this.packaging = packaging;
        this.deploymentsDir = deploymentsDir;
        this.projectBuildDir = projectBuildDir;
        this.javaDir = javaDir;
        this.contextRoot = contextRoot;
        this.cliSessions = cliSessions == null ? Collections.emptyList() : cliSessions;
        this.extraServerContent = extraServerContent == null ? Collections.emptyList() : extraServerContent;
        this.resources = resources == null ? Collections.emptySet() : resources;
        this.pluginConfigUpdated = pluginConfigUpdated;
        info("New project " + baseDir);
    }

    @Override
    public final Path getBaseDir() {
        return baseDir;
    }

    @Override
    public final Path getSourceDir() {
        return getBaseDir().resolve("src");
    }

    @Override
    public final Path getProjectBuildDir() {
        return projectBuildDir;
    }

    @Override
    public final Path getDeploymentsDir() {
        return deploymentsDir;
    }

    @Override
    public final Set<Path> getResources() {
        return resources;
    }

    @Override
    public final boolean isContextRoot() {
        return contextRoot;
    }

    @Override
    public final String getFinalName() {
        return finalName;
    }

    @Override
    public final Path getPomFile() {
        return getBaseDir().resolve("pom.xml");
    }

    @Override
    public final boolean isPluginConfigUpdated() throws ProjectBuildingException {
        checkPluginCalled = true;
        return pluginConfigUpdated;
    }

    @Override
    public final List<CliSession> getCliSessions() {
        return cliSessions;
    }

    @Override
    public final List<String> getExtraServerContent() {
        return extraServerContent;
    }

    @Override
    public final void debug(String msg) {
        System.out.println("[DEBUG] " + msg);
    }

    @Override
    public final void info(String msg) {
        System.out.println("[INFO] " + msg);

    }

    @Override
    public final String getPackaging() {
        return packaging;
    }

    @Override
    public final void cleanup(boolean autoCompile) throws MojoExecutionException {
        cleanupCalled = true;
    }

    @Override
    public final void compile(boolean autoCompile) throws MojoExecutionException {
        compileCalled = true;
    }

    @Override
    public final void packageJar(Path targetDir, Path artifactFile) throws IOException, MojoExecutionException {
        packageJarCalled = true;
    }

    @Override
    public final void packageWar(Path targetDir) throws MojoExecutionException {
        packageWarCalled = true;
    }

    void reset() {
        compileCalled = false;
        packageWarCalled = false;
        packageJarCalled = false;
        cleanupCalled = false;
        checkPluginCalled = false;
    }

    @Override
    public void deploy(Path dir) throws Exception {
        // NO OP.
    }

    @Override
    public void resources() throws MojoExecutionException {
        resourcesCalled = true;
    }

    @Override
    public List<String> getWebExtensions() {
       return Collections.emptyList();
    }

    @Override
    public Set<Path> getCompileRoots() {
        Set<Path> paths = new HashSet<>();
        paths.add(javaDir);
        return paths;
    }
}
