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

import java.io.File;
import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.eclipse.aether.repository.RemoteRepository;

import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.util.IoUtils;
import org.jboss.galleon.util.ZipUtils;
import org.twdata.maven.mojoexecutor.MojoExecutor;
import static org.twdata.maven.mojoexecutor.MojoExecutor.artifactId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.configuration;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executeMojo;
import static org.twdata.maven.mojoexecutor.MojoExecutor.executionEnvironment;
import static org.twdata.maven.mojoexecutor.MojoExecutor.goal;
import static org.twdata.maven.mojoexecutor.MojoExecutor.groupId;
import static org.twdata.maven.mojoexecutor.MojoExecutor.plugin;
import static org.twdata.maven.mojoexecutor.MojoExecutor.version;
import org.wildfly.core.launcher.Launcher;
import org.wildfly.plugins.bootablejar.maven.common.Utils;
import org.wildfly.plugins.bootablejar.maven.goals.DevWatchContext.BootableAppEventHandler;
import org.wildfly.plugins.bootablejar.maven.goals.DevWatchContext.ProjectContext;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.dmr.ModelNode;
import org.wildfly.plugin.common.PropertyNames;
import org.wildfly.plugin.core.ServerHelper;

/**
 * Build and start a bootable JAR for dev-watch mode. This goal monitors the
 * changes in the project and recompile/re-deploy. Type Ctrl-C to kill the
 * running server.
 *
 * @author jfdenise
 */
@Mojo(name = "dev-watch", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.COMPILE)
public final class DevWatchBootableJarMojo extends AbstractDevBootableJarMojo {

    private static final String MANAGEMENT_LAYER = "management";

    /**
     * running any one of these phases means the compile phase will have been
     * run, if these have not been run we manually run compile
     */
    private static final Set<String> POST_COMPILE_PHASES = new HashSet<>(Arrays.asList(
            "compile",
            "process-classes",
            "generate-test-sources",
            "process-test-sources",
            "generate-test-resources",
            "process-test-resources",
            "test-compile",
            "process-test-classes",
            "test",
            "prepare-package",
            "package",
            "pre-integration-test",
            "integration-test",
            "post-integration-test",
            "verify",
            "install",
            "deploy"));
    private static final String ORG_APACHE_MAVEN_PLUGINS = "org.apache.maven.plugins";
    private static final String MAVEN_COMPILER_PLUGIN = "maven-compiler-plugin";
    private static final String MAVEN_COMPILER_GOAL = "compile";
    private static final String MAVEN_WAR_PLUGIN = "maven-war-plugin";
    private static final String MAVEN_EXPLODED_GOAL = "exploded";
    private static final String MAVEN_JAR_PLUGIN = "maven-jar-plugin";
    private static final String MAVEN_EJB_PLUGIN = "maven-ejb-plugin";
    private static final String MAVEN_JAR_GOAL = "jar";
    private static final String MAVEN_EJB_GOAL = "ejb";
    private static final String MAVEN_RESOURCES_PLUGIN = "maven-resources-plugin";
    private static final String MAVEN_RESOURCES_GOAL = "resources";
    private static final String REBUILD_MARKER = "wildfly.bootable.jar.rebuild";

    private static final String MAVEN_WILDFLY_JAR_PLUGINS = "org.wildfly.plugins";
    private static final String MAVEN_WILDFLY_JAR_PLUGIN = "wildfly-jar-maven-plugin";
    private static final String WATCH_GOAL = "dev-watch";
    private static final boolean IS_WINDOWS;

    static {
        final String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        IS_WINDOWS = os.contains("win");
    }

    @Component
    private BuildPluginManager pluginManager;

    @Component
    private ProjectBuilder projectBuilder;

    @Parameter(defaultValue = "${project.build.sourceDirectory}")
    private File sourceDir;

    /**
     * Specifies the host name of the server where the deployment plan should be
     * executed.
     */
    @Parameter(defaultValue = "localhost", property = PropertyNames.HOSTNAME)
    private String hostname;

    /**
     * Specifies the port number the server is listening on.
     */
    @Parameter(defaultValue = "9990", property = PropertyNames.PORT)
    private int port;

    /**
     * The timeout, in seconds, to wait for a management connection.
     */
    @Parameter(property = PropertyNames.TIMEOUT, defaultValue = "60")
    protected int timeout;

    @Parameter(defaultValue = "${project.remotePluginRepositories}", readonly = true, required = true)
    private List<RemoteRepository> pluginRepos;

    private Process process;
    private Path currentServerDir;
    private final DeploymentController deploymentController = new DeploymentController();

    // Test specific content to have the process to exit on Windows
    static final String TEST_PROPERTY_EXIT = "dev-watch.test.exit.on.file";

    @Override
    protected void configureServer() {
        getLog().info("Dev mode, adding layer " + MANAGEMENT_LAYER + " to ensure dev mode can be operated");
        addExtraLayer(MANAGEMENT_LAYER);
    }

    private class DeploymentController {

        void deploy(Path dir) throws Exception {
            if (process == null) {
                return;
            }
            String name = dir.getFileName().toString();
            try (ModelControllerClient client = createClient()) {
                ServerHelper.waitForStandalone(client, timeout);
                undeploy(client, name);
                waitRemoved(client, name);
                boolean success = deploy(client, dir);
                if (success) {
                    waitDeploymentUp(client, name);
                }
                // We only need this on Windows since it may lock the JAR when the delete process is running
                if (IS_WINDOWS) {
                    currentServerDir = getHomeDirectory(client);
                }
            }
        }

        void waitRemoved(ModelControllerClient client, String name) throws Exception {
            ModelNode address = new ModelNode();
            address.add("deployment", name);
            waitStatus(client, "failed", Operations.createOperation("read-resource", address));
            getLog().debug("Deployment " + name + " removed");
        }

        void waitDeploymentUp(ModelControllerClient client, String name) throws Exception {
            ModelNode address = new ModelNode();
            address.add("deployment", name);
            ModelNode op = Operations.createOperation("read-attribute", address);
            op.get("name").set("status");
            ModelNode reply = waitStatus(client, "success", op);
            ModelNode result = reply.get("result");
            if (result.isDefined()) {
                String status = result.asString();
                if ("OK".equals(status)) {
                    getLog().debug("Deployment " + name + " is up");
                } else {
                    getLog().warn("Deployment " + name + " failed, status is " + status);
                }
            } else {
                throw new MojoExecutionException("No status returned for deployment " + name);
            }
        }

        void undeploy(ModelControllerClient client, String name) throws Exception {
            ModelNode composite = Operations.createCompositeOperation();
            ModelNode steps = composite.get("steps");
            ModelNode address = new ModelNode();
            address.add("deployment", name);
            steps.add(Operations.createOperation("undeploy", address));
            steps.add(Operations.createOperation("remove", address));
            getLog().debug("Undeploy " + name);
            client.execute(composite);
            getLog().debug("Undeploy " + name + " done");
        }

        boolean deploy(ModelControllerClient client, Path dir) throws Exception {
            ModelNode composite = Operations.createCompositeOperation();
            ModelNode steps = composite.get("steps");
            ModelNode address = new ModelNode();
            String name = dir.getFileName().toString();
            address.add("deployment", name);
            final ModelNode op = Operations.createOperation("add", address);
            ModelNode content = op.get("content").get(0);
            content.get("path").set(dir.toAbsolutePath().toString());
            content.get("archive").set(false);
            getLog().debug("Deploy " + name);
            steps.add(op);
            steps.add(Operations.createOperation("deploy", address));
            ModelNode reply = client.execute(composite);
            getLog().debug("Deploy " + name + " done");
            return "success".equals(reply.get("outcome").asString());
        }

        ModelNode waitStatus(ModelControllerClient client, String status, ModelNode op) throws Exception {
            int t = timeout * 1000;
            int waitTime = 100;
            while (t >= 0) {
                ModelNode reply = client.execute(op);
                if (status.equals(reply.get("outcome").asString())) {
                    return reply;
                }
                Thread.sleep(waitTime);
                t -= waitTime;
            }
            throw new MojoExecutionException("Timeout waiting for " + op + " to return " + status + " status");
        }
    }

    private class ProjectContextImpl implements DevWatchContext.ProjectContext {

        private final MavenProject currentProject;
        private final Xpp3Dom currentBootableJarConfig;
        private final Path projectBuildDir;
        private final boolean contextRoot;
        private final List<CliSession> cliSessions;
        private final List<String> extraServerContent;
        private final Path sourceDir;

        ProjectContextImpl(MavenProject currentProject,
                Xpp3Dom currentBootableJarConfig,
                Path projectBuildDir,
                Path sourceDir,
                boolean contextRoot,
                List<CliSession> cliSessions,
                List<String> extraServerContent) throws IOException {
            this.currentProject = currentProject;
            this.currentBootableJarConfig = currentBootableJarConfig;
            this.projectBuildDir = projectBuildDir;
            this.sourceDir = sourceDir;
            this.contextRoot = contextRoot;
            this.cliSessions = cliSessions;
            this.extraServerContent = extraServerContent;

        }

        @Override
        public final Path getBaseDir() {
            return currentProject.getBasedir().toPath();
        }

        @Override
        public final Path getSourceDir() {
            return getBaseDir().resolve("src");
        }

        @Override
        public final Path getJavaDir() {
            return sourceDir;
        }

        @Override
        public final Path getProjectBuildDir() {
            return projectBuildDir;
        }

        @Override
        public final Path getDeploymentsDir() {
            return DevWatchBootableJarMojo.this.getDeploymentsDir();
        }

        @Override
        public final Set<Path> getResources() {
            Set<Path> paths = new HashSet<>();
            for (Resource res : currentProject.getResources()) {
                paths.add(Paths.get(res.getDirectory()));
            }
            return paths;
        }

        @Override
        public final boolean isContextRoot() {
            return contextRoot;
        }

        @Override
        public final String getFinalName() {
            return currentProject.getBuild().getFinalName();
        }

        @Override
        public final Path getPomFile() {
            return currentProject.getBasedir().toPath().resolve("pom.xml");
        }

        @Override
        public final boolean isPluginConfigUpdated() throws ProjectBuildingException {
            MavenProject newProject = newProject(getPomFile());
            Plugin newPlugin = getPlugin(newProject);
            return !currentBootableJarConfig.equals(newPlugin.getConfiguration());
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
            getLog().debug(msg);
        }

        @Override
        public final void info(String msg) {
            getLog().info(msg);
        }

        @Override
        public final String getPackaging() {
            String packaging = currentProject.getPackaging();
            if ("ejb".equals(packaging)) {
                packaging = JAR;
            }
            return packaging;
        }

        @Override
        public final void cleanup() throws MojoExecutionException {
            getLog().debug("[WATCH] clean-up");
            IoUtils.recursiveDelete(getDeploymentsDir());
            cleanClasses(currentProject);
            triggerResources(currentProject);
        }

        @Override
        public final void compile(boolean autoCompile) throws MojoExecutionException {
            if (autoCompile) {
                handleAutoCompile(currentProject);
            } else {
                getLog().debug("[WATCH] compile");
                triggerCompile(currentProject);
            }
        }

        @Override
        public final void packageJar(Path targetDir, Path artifactFile) throws IOException, MojoExecutionException {
            triggerJar(currentProject);
            ZipUtils.unzip(artifactFile, targetDir);
        }

        @Override
        public final void packageWar(Path targetDir) throws MojoExecutionException {
            triggerExplodeWar(currentProject, targetDir);
        }

        @Override
        public void deploy(Path dir) throws Exception {
            deploymentController.deploy(dir);
        }

    }

    @Override
    protected void doExecute() throws MojoExecutionException, MojoFailureException {
        boolean isRebuild = System.getProperty(REBUILD_MARKER) != null;
        if (isRebuild) {
            return;
        }
        try {
            WatchService watcher = FileSystems.getDefault().newWatchService();
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        watcher.close();
                    } catch (IOException ex) {
                        getLog().error("Error closing the watcher " + ex);
                    }
                    shutdownContainer();
                }
            }));

            ProjectContext projectContext = new ProjectContextImpl(project,
                    (Xpp3Dom) getPlugin(project).getConfiguration(),
                    Paths.get(projectBuildDir), sourceDir.toPath(), contextRoot, cliSessions, extraServerContentDirs);
            DevWatchContext ctx = new DevWatchContext(projectContext, watcher);
            ctx.build(true);
            process = Launcher.of(buildCommandBuilder(false))
                    .inherit()
                    .launch();
            deploymentController.deploy(ctx.getTargetDirectory());
            watch(watcher, ctx);
        } catch (Exception e) {
            throw new MojoExecutionException(e.getLocalizedMessage(), e);
        }
    }

    private void watch(WatchService watcher, DevWatchContext ctx) throws IOException, MojoExecutionException, InterruptedException, MojoFailureException, ProjectBuildingException {
        boolean mustRebuildJar = false;
        String exitOnFile = System.getProperty(TEST_PROPERTY_EXIT);
        try {
            for (;;) {
                WatchKey key = watcher.take();
                BootableAppEventHandler handler = ctx.newEventHandler();
                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();
                    if (kind == OVERFLOW) {
                        continue;
                    }
                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    getLog().debug("[WATCH] file change [" + ev.kind().name() + "]: " + ev.context());
                    if (exitOnFile != null && exitOnFile.equals(ev.context().getFileName().toString())) {
                        getLog().info("Asked to exit by the test");
                        return;
                    }
                    Path absolutePath = ctx.getPath(key, ev.context());
                    if (absolutePath == null) {
                        continue;
                    }
                    getLog().debug("[WATCH] file change [" + ev.kind().name() + "]: " + absolutePath);
                    try {
                        handler.handle(ev.kind(), absolutePath);
                    } catch (Exception ex) {
                        getLog().error("[WATCH], exception handling file change: " + ex);
                    }
                }

                try {
                    if (handler.rebuildBootableJAR || mustRebuildJar) {
                        // We must first stop the server, on Windows platform
                        // we can't rebuild a Bootable JAR although the server is running.
                        getLog().info("[WATCH] stopping bootable JAR");
                        shutdownContainer();
                        getLog().info("[WATCH] server stopped");
                        // Must rebuild the bootable JAR.
                        System.setProperty(REBUILD_MARKER, "true");
                        getLog().info("[WATCH] re-building bootable JAR");
                        try {
                            ctx = triggerRebuildBootableJar(watcher, ctx);
                            mustRebuildJar = false;
                        } catch (Exception ex) {
                            // We are not able to rebuild the server, force rebuilding it
                            // for the next event.
                            mustRebuildJar = true;
                            throw ex;
                        }
                        // We were able to rebuild a bootable JAR
                        // can stop the server

                        handler = ctx.newEventHandler();
                        ctx.build(false);
                        process = Launcher.of(buildCommandBuilder(false))
                                .inherit()
                                .launch();
                        deploymentController.deploy(ctx.getTargetDirectory());
                        getLog().info("[WATCH] server re-started");
                    } else {
                        if (handler.reset) {
                            ctx = resetWatcher(watcher, ctx);
                            handler = ctx.newEventHandler();
                            ctx.build(false);
                        } else {
                            handler.applyChanges();
                        }
                    }
                } catch (Exception ex) {
                    getLog().error("Error rebuilding: " + ex);
                    Throwable cause = ex.getCause();
                    if (cause instanceof ProvisioningException) {
                        getLog().error(cause.getLocalizedMessage());
                    }
                    if (getLog().isDebugEnabled()) {
                        ex.printStackTrace();
                    }
                }
                key.reset();
            }
        } catch (ClosedWatchServiceException ex) {
            // OK Can ignore, we have been closed by shutdown hook.
        } finally {
            watcher.close();
        }
    }

    void handleAutoCompile(MavenProject project) throws MojoExecutionException {
        //we check to see if there was a compile (or later) goal before this plugin
        boolean compileNeeded = true;
        for (String goal : session.getGoals()) {
            if (POST_COMPILE_PHASES.contains(goal)) {
                compileNeeded = false;
                break;
            }
            if (goal.endsWith("wildfly-jar:" + WATCH_GOAL)) {
                break;
            }
        }

        //if the user did not compile we run it for them
        if (compileNeeded) {
            triggerCompile(project);
        }
    }

    void triggerCompile(MavenProject project) throws MojoExecutionException {
        // Compile the Java sources if needed
        final String compilerPluginKey = ORG_APACHE_MAVEN_PLUGINS + ":" + MAVEN_COMPILER_PLUGIN;
        final Plugin compilerPlugin = project.getPlugin(compilerPluginKey);
        if (compilerPlugin != null) {
            executeGoal(project, compilerPlugin, ORG_APACHE_MAVEN_PLUGINS, MAVEN_COMPILER_PLUGIN, MAVEN_COMPILER_GOAL,
                    getPluginConfig(compilerPlugin, MAVEN_COMPILER_GOAL));
        }
    }

    void triggerExplodeWar(MavenProject project, Path targetDir) throws MojoExecutionException {
        // Compile the Java sources if needed
        final String warPluginKey = ORG_APACHE_MAVEN_PLUGINS + ":" + MAVEN_WAR_PLUGIN;
        final Plugin warPlugin = project.getPlugin(warPluginKey);
        if (warPlugin != null) {
            executeGoal(project, warPlugin, ORG_APACHE_MAVEN_PLUGINS, MAVEN_WAR_PLUGIN, MAVEN_EXPLODED_GOAL, getPluginConfig(warPlugin, targetDir));
        } else {
            getLog().warn("Can't package war application, war plugin not found");
        }
    }

    void triggerJar(MavenProject project) throws MojoExecutionException {
        // Package as a jar
        final String ejbPluginKey = ORG_APACHE_MAVEN_PLUGINS + ":" + MAVEN_EJB_PLUGIN;
        final Plugin ejbPlugin = project.getPlugin(ejbPluginKey);
        if (ejbPlugin != null) {
            executeGoal(project, ejbPlugin, ORG_APACHE_MAVEN_PLUGINS, MAVEN_EJB_PLUGIN, MAVEN_EJB_GOAL,
                    getPluginConfig(ejbPlugin, MAVEN_EJB_GOAL));
        } else {
            final String jarPluginKey = ORG_APACHE_MAVEN_PLUGINS + ":" + MAVEN_JAR_PLUGIN;
            final Plugin jarPlugin = project.getPlugin(jarPluginKey);
            if (jarPlugin != null) {
                executeGoal(project, jarPlugin, ORG_APACHE_MAVEN_PLUGINS, MAVEN_JAR_PLUGIN, MAVEN_JAR_GOAL,
                        getPluginConfig(jarPlugin, MAVEN_JAR_GOAL));
            } else {
                getLog().warn("Can't package jar application, jar nor ejb plugins found");
            }
        }
    }

    Plugin getPlugin(MavenProject project) {
        final String jarPluginKey = MAVEN_WILDFLY_JAR_PLUGINS + ":" + MAVEN_WILDFLY_JAR_PLUGIN;
        return project.getPlugin(jarPluginKey);
    }

    MavenProject newProject(Path pomFile) throws ProjectBuildingException {
        ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
        buildingRequest.setResolveDependencies(true);
        return projectBuilder.build(pomFile.toFile(), buildingRequest).getProject();
    }

    private DevWatchContext resetWatcher(WatchService watcher, DevWatchContext ctx) throws MojoExecutionException, ProjectBuildingException, IOException {
        MavenProject mavenProject = newProject(project.getBasedir().toPath().resolve("pom.xml"));
        updateSession(mavenProject);
        ctx.cleanup();
        ProjectContext projectContext = new ProjectContextImpl(mavenProject,
                (Xpp3Dom) getPlugin(mavenProject).getConfiguration(),
                Paths.get(projectBuildDir), sourceDir.toPath(), contextRoot, cliSessions, extraServerContentDirs);
        return new DevWatchContext(projectContext, watcher);
    }

    private void updateSession(MavenProject newProject) {
        session.setCurrentProject(newProject);
        List<MavenProject> lst = new ArrayList<>();
        lst.add(newProject);
        session.setAllProjects(lst);
        session.setProjects(lst);
        Map<String, MavenProject> map = new HashMap<>();
        map.put(newProject.getGroupId() + ":" + newProject.getArtifactId() + ":" + newProject.getVersion(), newProject);
        session.setProjectMap(map);
    }

    private DevWatchContext triggerRebuildBootableJar(WatchService watcher, DevWatchContext ctx) throws MojoExecutionException, ProjectBuildingException, IOException {
        MavenProject mavenProject = newProject(project.getBasedir().toPath().resolve("pom.xml"));
        updateSession(mavenProject);
        final Plugin jarPlugin = getPlugin(mavenProject);
        Path updatedSrcDir = sourceDir.toPath();
        Path updatedBuildDir = Paths.get(projectBuildDir);
        boolean updatedContextRoot = true;
        List<String> updatedExtras = new ArrayList<>();
        List<CliSession> updatedCliSessions = new ArrayList<>();
        if (jarPlugin != null) {
            Xpp3Dom config = getBootableJarPluginConfig(jarPlugin);
            executeGoal(mavenProject, jarPlugin, MAVEN_WILDFLY_JAR_PLUGINS, MAVEN_WILDFLY_JAR_PLUGIN, WATCH_GOAL, config);

            // Resync the jvmArguments and arguments that we are going to re-use when launching the server
            Xpp3Dom jvmArguments = config.getChild("jvmArguments");
            this.jvmArguments.clear();
            if (jvmArguments != null) {
                //rebuild them.
                if (jvmArguments.getChildren() != null && jvmArguments.getChildren().length != 0) {
                    for (Xpp3Dom child : jvmArguments.getChildren()) {
                        this.jvmArguments.add(child.getValue());
                    }
                } else {
                    String value = resolve(jvmArguments.getValue());
                    if (value != null) {
                        this.jvmArguments.addAll(Utils.splitArguments(value));
                    }
                }
            }
            Xpp3Dom serverArguments = config.getChild("arguments");
            this.arguments.clear();
            if (serverArguments != null) {
                //rebuild them.
                if (serverArguments.getChildren() != null && serverArguments.getChildren().length != 0) {
                    for (Xpp3Dom child : serverArguments.getChildren()) {
                        this.arguments.add(child.getValue());
                    }
                } else {
                    String value = resolve(serverArguments.getValue());
                    if (value != null) {
                        this.arguments.addAll(Utils.splitArguments(value));
                    }
                }
            }
            Xpp3Dom srcDir = config.getChild("sourceDir");
            if (srcDir != null) {
                // Is null for defaultValue.
                String value = resolve(srcDir.getValue());
                if (value != null) {
                    updatedSrcDir = Paths.get(value);
                }
            }
            Xpp3Dom projectBuildDir = config.getChild("projectBuildDir");
            if (projectBuildDir != null) {
                // Is null for defaultValue.
                String value = resolve(projectBuildDir.getValue());
                if (value != null) {
                    updatedBuildDir = Paths.get(value);
                }
            }

            Xpp3Dom extra = config.getChild("extraServerContentDirs");
            if (extra != null) {
                if (extra.getChildren() != null && extra.getChildren().length != 0) {
                    for (Xpp3Dom child : extra.getChildren()) {
                        updatedExtras.add(child.getValue());
                    }
                } else {
                    String value = resolve(extra.getValue());
                    if (value != null) {
                        updatedExtras.addAll(Utils.splitArguments(value));
                    }
                }
            }

            Xpp3Dom cli = config.getChild("cliSessions");
            if (cli != null) {
                for (Xpp3Dom child : cli.getChildren()) {
                    CliSession session = new CliSession();
                    Xpp3Dom props = child.getChild("properties-file");
                    if (props == null) {
                        props = child.getChild("propertiesFile");
                    }
                    if (props != null) {
                        session.setPropertiesFile(props.getValue());
                    }
                    Xpp3Dom scripts = child.getChild("script-files");
                    if (scripts == null) {
                        scripts = child.getChild("scriptFiles");
                    }
                    if (scripts != null) {
                        List<String> lst = new ArrayList<>();
                        for (Xpp3Dom script : scripts.getChildren()) {
                            lst.add(script.getValue());
                        }
                        session.setScriptFiles(lst);
                    }
                    updatedCliSessions.add(session);
                }
            }
            Xpp3Dom ctxRoot = config.getChild("contextRoot");
            if (ctxRoot != null) {
                // Is null for defaultValue.
                String value = resolve(ctxRoot.getValue());
                if (value != null) {
                    updatedContextRoot = Boolean.valueOf(value);
                }
            }
            Xpp3Dom hostname = config.getChild("hostname");
            if (hostname != null) {
                // Is null for defaultValue.
                String value = resolve(hostname.getValue());
                if (value != null) {
                    this.hostname = value;
                }
            }
            Xpp3Dom port = config.getChild("port");
            if (port != null) {
                // Is null for defaultValue.
                String value = resolve(port.getValue());
                if (value != null) {
                    this.port = Integer.parseInt(value);
                }
            }

            Xpp3Dom timeout = config.getChild("timeout");
            if (timeout != null) {
                // Is null for defaultValue.
                String value = resolve(timeout.getValue());
                if (value != null) {
                    this.timeout = Integer.parseInt(value);
                }
            }
        }
        ctx.cleanup();
        ProjectContext projectContext = new ProjectContextImpl(mavenProject,
                (Xpp3Dom) getPlugin(mavenProject).getConfiguration(),
                updatedBuildDir, updatedSrcDir, updatedContextRoot, updatedCliSessions, updatedExtras);
        return new DevWatchContext(projectContext, watcher);
    }

    private String resolve(String value) {
        if (value != null) {
            if (value.startsWith("${")) {
                String systemProp = value.substring(2, value.length() - 1);
                value = System.getProperty(systemProp);
            }
        }
        return value;
    }

    private void executeGoal(MavenProject project, Plugin plugin, String groupId, String artifactId, String goal, Xpp3Dom config) throws MojoExecutionException {
        executeMojo(
                plugin(
                        groupId(groupId),
                        artifactId(artifactId),
                        version(plugin.getVersion()),
                        plugin.getDependencies()),
                goal(goal),
                config,
                executionEnvironment(
                        project,
                        session,
                        pluginManager));
    }

    void triggerResources(MavenProject project) throws MojoExecutionException {
        List<Resource> resources = project.getResources();
        if (resources.isEmpty()) {
            return;
        }
        Plugin resourcesPlugin = project.getPlugin(ORG_APACHE_MAVEN_PLUGINS + ":" + MAVEN_RESOURCES_PLUGIN);
        if (resourcesPlugin == null) {
            return;
        }
        executeGoal(project, resourcesPlugin, ORG_APACHE_MAVEN_PLUGINS, MAVEN_RESOURCES_PLUGIN, MAVEN_RESOURCES_GOAL,
                getPluginConfig(resourcesPlugin, MAVEN_RESOURCES_GOAL));
    }

    void cleanClasses(MavenProject project) throws MojoExecutionException {
        Path buildDir = Paths.get(this.projectBuildDir);
        IoUtils.recursiveDelete(Paths.get(project.getBuild().getOutputDirectory()));
        final String compilerPluginKey = ORG_APACHE_MAVEN_PLUGINS + ":" + MAVEN_COMPILER_PLUGIN;
        final Plugin compilerPlugin = project.getPlugin(compilerPluginKey);
        if (compilerPlugin != null) {
            Path p;
            Xpp3Dom config = getPluginConfig(compilerPlugin, MAVEN_COMPILER_GOAL);
            Xpp3Dom genSources = config.getChild("generatedSourcesDirectory");
            if (genSources == null) {
                p = buildDir.resolve("generated-sources").resolve("annotations");
            } else {
                String path = genSources.getValue();
                p = Paths.get(path);
            }
            IoUtils.recursiveDelete(p);
        }
    }

    private Xpp3Dom getPluginConfig(Plugin plugin, Path target) {

        Xpp3Dom configuration = configuration();

        Xpp3Dom pluginConfiguration = (Xpp3Dom) plugin.getConfiguration();
        if (pluginConfiguration != null) {
            //Filter out `test*` configurations
            for (Xpp3Dom child : pluginConfiguration.getChildren()) {
                if (!child.getName().startsWith("test") && !child.getName().startsWith("failOnMissingWebXml")) {
                    configuration.addChild(child);
                }
            }
        }
        MojoExecutor.Element e = new MojoExecutor.Element("webappDirectory", target.toAbsolutePath().toString());
        configuration.addChild(e.toDom());
        return configuration;
    }

    private Xpp3Dom getBootableJarPluginConfig(Plugin plugin) {

        Xpp3Dom configuration = configuration();

        Xpp3Dom pluginConfiguration = (Xpp3Dom) plugin.getConfiguration();
        if (pluginConfiguration != null) {
            for (Xpp3Dom child : pluginConfiguration.getChildren()) {
                String camelName = camelize(child.getName());
                Xpp3Dom dom = new Xpp3Dom(camelName);
                if (child.getValue() != null) {
                    dom.setValue(child.getValue());
                }
                for (String attribute : child.getAttributeNames()) {
                    dom.setAttribute(attribute, child.getAttribute(attribute));
                }
                for (Xpp3Dom d : child.getChildren()) {
                    dom.addChild(d);
                }
                configuration.addChild(dom);
            }
        }

        return configuration;
    }

    private Xpp3Dom getPluginConfig(Plugin plugin, String goal) throws MojoExecutionException {
        Xpp3Dom mergedConfig = null;
        if (!plugin.getExecutions().isEmpty()) {
            for (PluginExecution exec : plugin.getExecutions()) {
                if (exec.getConfiguration() != null && exec.getGoals().contains(goal)) {
                    mergedConfig = mergedConfig == null ? (Xpp3Dom) exec.getConfiguration()
                            : Xpp3Dom.mergeXpp3Dom(mergedConfig, (Xpp3Dom) exec.getConfiguration(), true);
                }
            }
        }

        if ((Xpp3Dom) plugin.getConfiguration() != null) {
            mergedConfig = mergedConfig == null ? (Xpp3Dom) plugin.getConfiguration()
                    : Xpp3Dom.mergeXpp3Dom(mergedConfig, (Xpp3Dom) plugin.getConfiguration(), true);
        }

        final Xpp3Dom configuration = configuration();

        if (mergedConfig != null) {
            Set<String> supportedParams = null;
            // Filter out `test*` configurations
            for (Xpp3Dom child : mergedConfig.getChildren()) {
                if (child.getName().startsWith("test")) {
                    continue;
                }
                if (supportedParams == null) {
                    supportedParams = getMojoDescriptor(plugin, goal).getParameterMap().keySet();
                }
                if (supportedParams.contains(child.getName())) {
                    configuration.addChild(child);
                }
            }
        }

        return configuration;
    }

    // Required to retrieve the actual set of supported configuration items.
    private MojoDescriptor getMojoDescriptor(Plugin plugin, String goal) throws MojoExecutionException {
        try {
            return pluginManager.getMojoDescriptor(plugin, goal, pluginRepos, repoSession);
        } catch (Exception e) {
            throw new MojoExecutionException(
                    "Failed to obtain descriptor for Maven plugin " + plugin.getId() + " goal " + goal, e);
        }
    }

    private ModelControllerClient createClient() throws UnknownHostException {
        return ModelControllerClient.Factory.create(hostname, port);
    }

    private void shutdownContainer() {
        if (process != null) {
            if (process.isAlive()) {
                // Attempt to safely shutdown first
                try (ModelControllerClient client = createClient()) {
                    ServerHelper.shutdownStandalone(client, timeout);
                } catch (Throwable ignore) {
                    process.destroy();
                }
                try {
                    if (!process.waitFor(timeout, TimeUnit.SECONDS)) {
                        process.destroyForcibly();
                    }
                } catch (InterruptedException ex) {
                    getLog().error("Error waiting for process to terminate " + ex);
                }
            }
            process = null;
            // Make a timed attempt to wait for the directory to be deleted. In some cases the delete may still be
            // happening and the JAR may be in use on Windows.
            if (currentServerDir != null && Files.exists(currentServerDir.resolve("wildfly-cleanup-marker"))) {
                try {
                    int timeout = this.timeout * 1000;
                    while (Files.exists(currentServerDir)) {
                        TimeUnit.MILLISECONDS.sleep(500L);
                        timeout -= 500;
                        if (timeout <= 0) {
                            getLog().warn(String.format("Failed to wait for server directory to be deleted: %s", currentServerDir));
                            break;
                        }
                    }
                } catch (InterruptedException ignore) {
                } finally {
                    currentServerDir = null;
                }
            }
        }
    }

    private Path getHomeDirectory(final ModelControllerClient client) throws IOException {
        final ModelNode op = Operations.createReadAttributeOperation(
                Operations.createAddress("core-service", "server-environment"), "home-dir");
        final ModelNode result = client.execute(op);
        if (Operations.isSuccessfulOutcome(result)) {
            return Paths.get(Operations.readResult(result).asString());
        }
        getLog().warn(String.format("Failed to find home directory: %s", Operations.getFailureDescription(result).asString()));
        return null;
    }

    private static String camelize(final String name) {
        StringBuilder buf = null;

        final int length = name.length();
        for (int i = 0; i < length; i++) {
            if ('-' == name.charAt(i)) {
                buf = new StringBuilder(name.substring(0, i));
                break;
            }
        }

        if (buf == null) {
            return name;
        }

        boolean capitalize = true;
        for (int i = buf.length() + 1; i < length; i++) {
            final char c = name.charAt(i);
            if ('-' == c) {
                capitalize = true;
            } else if (capitalize) {
                buf.append(Character.toTitleCase(c));
                capitalize = false;
            } else {
                buf.append(c);
            }
        }

        return buf.toString();
    }

}
