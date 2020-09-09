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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;
import javax.xml.stream.XMLStreamException;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptorBuilder;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.shared.artifact.ArtifactCoordinate;
import org.codehaus.plexus.configuration.PlexusConfigurationException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.galleon.Constants;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.config.ConfigId;
import org.jboss.galleon.config.ConfigModel;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.maven.plugin.util.MavenArtifactRepositoryManager;
import org.jboss.galleon.maven.plugin.util.MvnMessageWriter;
import org.jboss.galleon.runtime.FeaturePackRuntime;
import org.jboss.galleon.runtime.ProvisioningRuntime;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.jboss.galleon.util.IoUtils;
import org.jboss.galleon.util.ZipUtils;
import org.jboss.galleon.xml.ProvisioningXmlParser;
import org.jboss.galleon.xml.ProvisioningXmlWriter;
import org.wildfly.plugins.bootablejar.maven.cli.CLIExecutor;
import org.wildfly.plugins.bootablejar.maven.cli.LocalCLIExecutor;
import org.wildfly.plugins.bootablejar.maven.cli.RemoteCLIExecutor;
import org.wildfly.plugins.bootablejar.maven.common.FeaturePack;
import org.wildfly.plugins.bootablejar.maven.common.LegacyPatchCleaner;
import org.wildfly.plugins.bootablejar.maven.common.MavenRepositoriesEnricher;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Build a bootable jar containing application and provisioned server
 *
 * @author jfdenise
 */
public class AbstractBuildBootableJarMojo extends AbstractMojo {

    public static final String BOOTABLE_SUFFIX = "bootable";
    public static final String JAR = "jar";
    public static final String WAR = "war";
    private static final String MODULE_ID_JAR_RUNTIME = "org.wildfly.bootable-jar";

    private static final String BOOT_ARTIFACT_ID = "wildfly-jar-boot";

    @Component
    RepositorySystem repoSystem;

    @Component
    MavenProjectHelper projectHelper;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    List<RemoteRepository> repositories;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    MavenSession session;

    /**
     * Arbitrary Galleon options used when provisioning the server. In case you
     * are building a large amount of bootable JAR in the same maven session, it
     * is strongly advised to set 'jboss-fork-embedded' option to 'true' in
     * order to fork Galleon provisioning and CLI scripts execution in dedicated
     * processes. For example:
     * <br/>
     * &lt;plugin-options&gt;<br/>
     * &lt;jboss-fork-embedded&gt;true&lt;/jboss-fork-embedded&gt;<br/>
     * &lt;/plugin-options&gt;
     */
    @Parameter(alias = "plugin-options", required = false)
    Map<String, String> pluginOptions = Collections.emptyMap();

    /**
     * Whether to use offline mode when the plugin resolves an artifact. In
     * offline mode the plugin will only use the local Maven repository for an
     * artifact resolution.
     */
    @Parameter(alias = "offline", defaultValue = "false")
    boolean offline;

    /**
     * Whether to log provisioning time at the end
     */
    @Parameter(alias = "log-time", defaultValue = "false")
    boolean logTime;

    /**
     * A list of galleon layers to provision. Can be used when
     * feature-pack-location or feature-packs are set.
     */
    @Parameter(alias = "layers", required = false)
    List<String> layers = Collections.emptyList();

    /**
     * A list of galleon layers to exclude. Can be used when
     * feature-pack-location or feature-packs are set.
     */
    @Parameter(alias = "excluded-layers", required = false)
    List<String> excludedLayers = Collections.emptyList();

    /**
     * Whether to record provisioned state in .galleon directory.
     */
    @Parameter(alias = "record-state", defaultValue = "false")
    boolean recordState;

    /**
     * Project build dir.
     */
    @Parameter(defaultValue = "${project.build.directory}")
    String projectBuildDir;

    /**
     * To make the war registered under root resource path ('/').
     */
    @Parameter(alias = "context-root", defaultValue = "true", property = "wildfly.bootable.context.root")
    boolean contextRoot;

    /**
     * The WildFly galleon feature-pack location to use if no provisioning.xml
     * file found. Can't be used in conjunction with feature-packs.
     */
    @Parameter(alias = "feature-pack-location", required = false,
            property = "wildfly.bootable.fpl")
    String featurePackLocation;

    /**
     * List of CLI execution sessions. An embedded server is started for each CLI session.
     * CLI session are configured in the following way:
     * <br/>
     * &lt;cli-sessions&gt;<br/>
     * &lt;cli-session&gt;<br/>
     * &lt;script-files&gt;<br/>
     * &lt;script&gt;../scripts/script1.cli&lt;/script&gt;<br/>
     * &lt;/script-files&gt;<br/>
     * &lt;!-- Expressions resolved during server execution --&gt;<br/>
     * &lt;resolve-expressions&gt;false&lt;/resolve-expressions&gt;<br/>
     * &lt;/cli-session&gt;<br/>
     * &lt;cli-session&gt;<br/>
     * &lt;script-files&gt;<br/>
     * &lt;script&gt;../scripts/script2.cli&lt;/script&gt;<br/>
     * &lt;/script-files&gt;<br/>
     * &lt;properties-file&gt;../scripts/cli.properties&lt;/properties-file&gt;<br/>
     * &lt;/cli-session&gt;<br/>
     * &lt;/cli-sessions&gt;
     */
    @Parameter(alias = "cli-sessions")
    List<CliSession> cliSessions = Collections.emptyList();

    /**
     * Hollow jar. Create a bootable jar that doesn't contain application.
     */
    @Parameter(alias = "hollow-jar", property = "wildfly.bootable.hollow")
    boolean hollowJar;

    /**
     * Set to {@code true} if you want the deployment to be skipped, otherwise
     * {@code false}.
     */
    @Parameter(defaultValue = "false", property = "wildfly.bootable.package.skip")
    boolean skip;

    /**
     * By default the generated jar is ${project.build.finalName}-bootable.jar
     */
    @Parameter(alias = "output-file-name", property = "wildfly.bootable.package.output.file.name")
    String outputFileName;

    /**
     * A list of feature-pack configurations to install, can be combined with
     * layers. Overrides galleon/provisioning.xml file. Can't be used in
     * conjunction with feature-pack-location.
     */
    @Parameter(alias = "feature-packs", required = false)
    private List<FeaturePack> featurePacks = Collections.emptyList();

    /**
     * A list of directories to copy content to the provisioned server.
     */
    @Parameter(alias = "extra-server-content-dirs", property = "wildfly.bootable.package.extra.server.content.dirs")
    List<String> extraServerContent = Collections.emptyList();

    /**
     * The path to the {@code provisioning.xml} file to use. Note that this cannot be used with the {@code feature-packs}
     * or {@code layers} configuration parameters.
     */
    @Parameter(alias = "provisioning-file", property = "wildfly.bootable.provisioning.file", defaultValue = "${project.basedir}/galleon/provisioning.xml")
    private File provisioningFile;

    /**
     * Path to a CLI script that applies legacy patches. Content of such script
     * should be composed of 'patch apply [path to zip file] [patch apply
     * options]' commands. Due to the nature of a bootable JAR trimmed with
     * Galleon, part of the content of the patch can be missing. In order to
     * force the patch to apply use the '--override-all' option. The
     * '--distribution' option is not needed, System property 'jboss.home.dir'
     * is automatically set to the server that will be packaged in the bootable
     * JAR. NB: The server is patched with a legacy patch right after the server
     * has been provisioned with Galleon.
     */
    @Parameter(alias = "legacy-patch-cli-script")
    String legacyPatchCliScript;

    /**
     * Set to true to enable patch cleanup. When cleanup is enabled, unused
     * added modules, patched modules original directories, unused overlay
     * directories and .installation/patches directory are deleted.
     */
    @Parameter(alias = "legacy-patch-clean-up", defaultValue = "false")
    boolean legacyPatchCleanUp;

    /**
     * By default executed CLI scripts output is not shown if execution is
     * successful. In order to display the CLI output, set this option to true.
     */
    @Parameter(alias = "display-cli-scripts-output")
    boolean displayCliScriptsOutput;

    /**
     * Overrides the default {@code logging.properties} the container uses when booting.
     * <br/>
     * In most cases this should not be required. The use-case is when the generated {@code logging.properties} causes
     * boot errors or you do not use the logging subsystem and would like to use a custom logging configuration.
     * <br/>
     * An example of a boot error would be using a log4j appender as a {@code custom-handler}.
     */
    @Parameter(alias = "boot-logging-config", property = "wildfly.bootable.logging.config")
    private File bootLoggingConfig;

    /**
     * By default, when building a bootable jar, the plugin extracts build artifacts in the directory
     * 'bootable-jar-build-artifacts'. You can use this property to change this directory name. In most cases
     * this should not be required. The use-case is when building multiple bootable JARs in the same project
     * on Windows Platform. In this case, each execution should have its own directory, the plugin being
     * unable to delete the directory due to some references to JBoss module files.
     */
    @Parameter(alias = "bootable-jar-build-artifacts", property = "wildfly.bootable.jar.build.artifacts", defaultValue = "bootable-jar-build-artifacts")
    private String bootableJarBuildArtifacts;

    @Inject
    private BootLoggingConfiguration bootLoggingConfiguration;

    private final Set<String> extraLayers = new HashSet<>();

    private Path wildflyDir;

    private MavenRepoManager artifactResolver;

    private final Set<Artifact> cliArtifacts = new HashSet<>();

    private boolean forkCli;

    public Path getJBossHome() {
        return wildflyDir;
    }

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        MavenRepositoriesEnricher.enrich(session, project, repositories);
        artifactResolver = offline ? new MavenArtifactRepositoryManager(repoSystem, repoSession)
                : new MavenArtifactRepositoryManager(repoSystem, repoSession, repositories);

        if (outputFileName == null) {
            outputFileName = this.project.getBuild().getFinalName() + "-" + BOOTABLE_SUFFIX + "." + JAR;
        }

        validateProjectFile();

        if (System.getProperty("dev") != null) {
            Path deployments = getDeploymentsDir();
            IoUtils.recursiveDelete(deployments);
            try {
                Files.createDirectory(deployments);
                copyProjectFile(deployments);
            } catch (IOException ex) {
                throw new MojoExecutionException("Fail creating deployments directory ", ex);
            }
            return;
        }
        Path contentRoot = Paths.get(project.getBuild().getDirectory()).resolve(bootableJarBuildArtifacts);
        if (Files.exists(contentRoot)) {
            deleteDir(contentRoot);
        }
        Path jarFile = Paths.get(project.getBuild().getDirectory()).resolve(outputFileName);
        IoUtils.recursiveDelete(contentRoot);

        wildflyDir = contentRoot.resolve("wildfly");
        Path contentDir = contentRoot.resolve("jar-content");
        try {
            Files.createDirectories(contentRoot);
            Files.createDirectories(contentDir);
            Files.deleteIfExists(jarFile);
        } catch (IOException ex) {
            throw new MojoExecutionException("Packaging wildfly failed", ex);
        }
        Artifact bootArtifact;
        try {
            bootArtifact = provisionServer(wildflyDir, contentDir.resolve("provisioning.xml"));
        } catch (ProvisioningException | IOException | XMLStreamException ex) {
            throw new MojoExecutionException("Provisioning failed", ex);
        }

        try {
            // We are forking CLI executions in order to avoid JBoss Modules static references to ModuleLoaders.
            forkCli = Boolean.parseBoolean(pluginOptions.getOrDefault("jboss-fork-embedded", "false"));
            if (forkCli) {
                getLog().info("CLI executions are done in forked process");
            }
            // Legacy Patching point
            legacyPatching();
            copyExtraContentInternal(wildflyDir, contentDir);
            copyExtraContent(wildflyDir);
            List<String> commands = new ArrayList<>();
            deploy(commands);
            List<String> serverConfigCommands = new ArrayList<>();
            configureCli(serverConfigCommands);
            commands.addAll(serverConfigCommands);
            if (!commands.isEmpty()) {
                executeCliScript(wildflyDir, commands, null, false, "Server configuration", true);
                if (!serverConfigCommands.isEmpty()) {
                    // Store generated commands to file in build artifacts.
                    Path genCliScript = contentRoot.resolve("generated-cli-script.txt");
                    try (BufferedWriter writer = Files.newBufferedWriter(genCliScript, StandardCharsets.UTF_8)) {
                        for (String str : serverConfigCommands) {
                            writer.write(str);
                            writer.newLine();
                        }
                    }
                    getLog().info("Stored CLI script executed to update server configuration in " + genCliScript + " file.");
                }
            }
            userScripts(wildflyDir, cliSessions, true);
            if (bootLoggingConfig == null) {
                generateLoggingConfig(wildflyDir);
            } else {
                // Copy the user overridden logging.properties
                final Path loggingConfig = bootLoggingConfig.toPath();
                if (Files.notExists(loggingConfig)) {
                    throw new MojoExecutionException(String.format("The bootLoggingConfig %s does not exist.", bootLoggingConfig));
                }
                final Path target = getJBossHome().resolve("standalone").resolve("configuration").resolve("logging.properties");
                Files.copy(loggingConfig, target, StandardCopyOption.REPLACE_EXISTING);
            }
            cleanupServer(wildflyDir);
            zipServer(wildflyDir, contentDir);
            buildJar(contentDir, jarFile, bootArtifact);
        } catch (Exception ex) {
            if (ex instanceof MojoExecutionException) {
                throw (MojoExecutionException) ex;
            } else if (ex instanceof MojoFailureException) {
                throw (MojoFailureException) ex;
            }
            throw new MojoExecutionException("Packaging wildfly failed", ex);
        } finally {
            // Although cli and embedded are run in their own classloader,
            // the module.path system property has been set and needs to be cleared for
            // in same JVM next execution.
            System.clearProperty("module.path");
        }

        attachJar(jarFile);
    }

    private void legacyPatching() throws Exception {
        if (legacyPatchCliScript != null) {
            LegacyPatchCleaner patchCleaner = null;
            if (legacyPatchCleanUp) {
                patchCleaner = new LegacyPatchCleaner(wildflyDir, getLog());
            }
            String prop = "jboss.home.dir";
            System.setProperty(prop, wildflyDir.toAbsolutePath().toString());
            try {
                List<CliSession> cliPatchingSessions = new ArrayList<>();
                List<String> files = new ArrayList<>();
                files.add(legacyPatchCliScript);
                CliSession patchingSession = new CliSession();
                patchingSession.setResolveExpressions(true);
                patchingSession.setScriptFiles(files);
                cliPatchingSessions.add(patchingSession);
                getLog().info("Patching server with " + legacyPatchCliScript + " CLI script.");
                userScripts(wildflyDir, cliPatchingSessions, false);
                if (patchCleaner != null) {
                    patchCleaner.clean();
                }
            } finally {
                System.clearProperty(prop);
            }
        }
    }

    private void copyExtraContent(Path wildflyDir) throws Exception {
        for (String path : extraServerContent) {
            Path extraContent = Paths.get(path);
            if (!Files.exists(extraContent)) {
                throw new Exception("Extra content dir " + path + " doesn't exist");
            }
            IoUtils.copy(extraContent, wildflyDir);
        }
    }

    protected void copyExtraContentInternal(Path wildflyDir, Path contentDir) throws Exception {

    }

    protected void addExtraLayer(String layer) {
        extraLayers.add(layer);
    }

    private void copyProjectFile(Path targetDir) throws IOException, MojoExecutionException {
        if (hollowJar) {
            getLog().info("Hollow jar, No application deployment added to server.");
            return;
        }
        File f = validateProjectFile();

        String fileName = f.getName();
        if (project.getPackaging().equals(WAR) || fileName.endsWith(WAR)) {
            if (contextRoot) {
                fileName = "ROOT." + WAR;
            }
        }
        Files.copy(f.toPath(), targetDir.resolve(fileName));
    }

    protected Path getDeploymentsDir() {
        return Paths.get(project.getBuild().getDirectory()).resolve("deployments");
    }

    protected void configureCli(List<String> commands) {

    }

    private void cleanupServer(Path jbossHome) throws IOException {
        Path history = jbossHome.resolve("standalone").resolve("configuration").resolve("standalone_xml_history");
        IoUtils.recursiveDelete(history);
        Files.deleteIfExists(jbossHome.resolve("README.txt"));
    }

    protected File validateProjectFile() throws MojoExecutionException {
        File f = getProjectFile();
        if (f == null && !hollowJar) {
            throw new MojoExecutionException("Cannot package without a primary artifact; please `mvn package` prior to invoking wildfly-jar:package from the command-line");
        }
        return f;
    }

    private void userScripts(Path wildflyDir, List<CliSession> sessions, boolean startEmbedded) throws Exception {
        for (CliSession session : sessions) {
            List<String> commands = new ArrayList<>();
            for (String path : session.getScriptFiles()) {
                File f = new File(path);
                if (!f.exists()) {
                    if (!f.isAbsolute()) {
                        f = Paths.get(project.getBasedir().getAbsolutePath()).resolve(f.toPath()).toFile();
                    }
                    if (!f.exists()) {
                        throw new RuntimeException("Cli script file " + path + " doesn't exist");
                    }
                }
                try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
                    String line = reader.readLine();
                    while (line != null) {
                        commands.add(line.trim());
                        line = reader.readLine();
                    }
                }
            }
            if(!commands.isEmpty()) {
                executeCliScript(wildflyDir, commands, session.getPropertiesFile(),
                        session.getResolveExpression(), session.toString(), startEmbedded);
            }
        }
    }

    private void executeCliScript(Path jbossHome, List<String> commands, String propertiesFile,
            boolean resolveExpression, String message, boolean startEmbedded) throws Exception {
        getLog().info("Executing CLI, " + message);
        Properties props = null;
        if (propertiesFile != null) {
            props = loadProperties(propertiesFile);
        }
        try {
            processCLI(jbossHome, commands, resolveExpression, startEmbedded);
        } finally {
            if (props != null) {
                for (String key : props.stringPropertyNames()) {
                    WildFlySecurityManager.clearPropertyPrivileged(key);
                }
            }
        }
    }

    private void generateLoggingConfig(final Path wildflyDir) throws Exception {
        try (CLIExecutor cmdCtx = forkCli ? new RemoteCLIExecutor(wildflyDir, getCLIArtifacts(), this, false)
                : new LocalCLIExecutor(wildflyDir, getCLIArtifacts(), this, false, bootLoggingConfiguration)) {
            try {
                cmdCtx.generateBootLoggingConfig();
            } catch (Exception e) {
                getLog().error("Failed to generate logging configuration: " + cmdCtx.getOutput());
                throw e;
            }
        }
    }

    private void processCLI(Path jbossHome, List<String> commands,
            boolean resolveExpression, boolean startEmbedded) throws Exception {

        List<String> allCommands = new ArrayList<>();
        if (startEmbedded) {
            allCommands.add("embed-server --jboss-home=" + jbossHome + " --std-out=discard");
        }
        for (String line : commands) {
            allCommands.add(line.trim());
        }
        if (startEmbedded) {
            allCommands.add("stop-embedded-server");
        }
        try (CLIExecutor executor = forkCli ? new RemoteCLIExecutor(jbossHome, getCLIArtifacts(), this, resolveExpression)
                : new LocalCLIExecutor(jbossHome, getCLIArtifacts(), this, resolveExpression, bootLoggingConfiguration)) {

            try {
                executor.execute(allCommands);
            } catch (Exception ex) {
                getLog().error("Error executing CLI script " + ex.getLocalizedMessage());
                getLog().error(executor.getOutput());
                throw ex;
            }
            if (displayCliScriptsOutput) {
                getLog().info(executor.getOutput());
            }
        }
        getLog().info("CLI scripts execution done.");
    }

    private List<Path> getCLIArtifacts() throws MojoExecutionException {
        getLog().debug("CLI artifacts " + cliArtifacts);
        List<Path> paths = new ArrayList<>();
        paths.add(wildflyDir.resolve("jboss-modules.jar"));
        for (Artifact a : cliArtifacts) {
            paths.add(resolveArtifact(a));
        }
        return paths;
    }

    public Level disableLog() {
        Logger l = Logger.getLogger("");
        Level level = l.getLevel();
        // Only disable logging if debug is not ebnabled.
        if (!getLog().isDebugEnabled()) {
            l.setLevel(Level.OFF);
        }
        return level;
    }

    public void enableLog(Level level) {
        Logger l = Logger.getLogger("");
        l.setLevel(level);
    }

    private Properties loadProperties(String propertiesFile) throws Exception {
        File f = new File(propertiesFile);
        if (!f.exists()) {
            if (!f.isAbsolute()) {
                f = Paths.get(project.getBasedir().getAbsolutePath()).resolve(f.toPath()).toFile();
            }
            if (!f.exists()) {
                throw new RuntimeException("Cli properties file " + f + " doesn't exist");
            }
        }
        final Properties props = new Properties();
        try (InputStreamReader inputStreamReader = new InputStreamReader(new FileInputStream(f),
            StandardCharsets.UTF_8)) {
            props.load(inputStreamReader);
        } catch (IOException e) {
            throw new Exception(
                "Failed to load properties from " + propertiesFile + ": " + e.getLocalizedMessage());
        }
        for (String key : props.stringPropertyNames()) {
            WildFlySecurityManager.setPropertyPrivileged(key, props.getProperty(key));
        }
        return props;
    }

    private File getProjectFile() {
        if (this.project.getArtifact().getFile() != null) {
            return this.project.getArtifact().getFile();
        }

        String finalName = this.project.getBuild().getFinalName();

        Path candidate = Paths.get(this.projectBuildDir, finalName + "." + this.project.getPackaging());

        if (Files.exists(candidate)) {
            return candidate.toFile();
        }
        return null;
    }

    protected Path getProvisioningFile() {
        return provisioningFile.toPath();
    }

    protected boolean hasLayers() {
        return !layers.isEmpty();
    }

    protected List<String> getLayers() {
        return layers;
    }

    protected List<String> getExcludedLayers() {
        return excludedLayers;
    }

    private Artifact provisionServer(Path home, Path outputProvisioningFile) throws ProvisioningException, MojoExecutionException, IOException, XMLStreamException {
        final Path provisioningFile = getProvisioningFile();
        ProvisioningConfig.Builder state = null;
        ProvisioningConfig config;
        try (ProvisioningManager pm = ProvisioningManager.builder().addArtifactResolver(artifactResolver)
                .setInstallationHome(home)
                .setMessageWriter(new MvnMessageWriter(getLog()))
                .setLogTime(logTime)
                .setRecordState(recordState)
                .build()) {

            ConfigModel.Builder configBuilder = null;
            ConfigId defaultConfig = null;
            if (!featurePacks.isEmpty()) {
                if (Files.exists(provisioningFile)) {
                    getLog().warn("Feature packs defined in pom.xml override provisioning file located in " + provisioningFile);
                }
                if (featurePackLocation != null) {
                    throw new MojoExecutionException("Feature packlocation can't be used with a list of feature-packs");
                }
                state = ProvisioningConfig.builder();

                for (FeaturePack fp : featurePacks) {

                    if (fp.getLocation() == null && (fp.getGroupId() == null || fp.getArtifactId() == null)
                            && fp.getNormalizedPath() == null) {
                        throw new MojoExecutionException("Feature-pack location, Maven GAV or feature pack path is missing");
                    }

                    final FeaturePackLocation fpl;
                    if (fp.getNormalizedPath() != null) {
                        fpl = pm.getLayoutFactory().addLocal(fp.getNormalizedPath(), false);
                    } else if (fp.getGroupId() != null && fp.getArtifactId() != null) {
                        Path path = resolveMaven(fp);
                        fpl = pm.getLayoutFactory().addLocal(path, false);
                    } else {
                        fpl = FeaturePackLocation.fromString(fp.getLocation());
                    }

                    final FeaturePackConfig.Builder fpConfig = FeaturePackConfig.builder(fpl);
                    fpConfig.setInheritConfigs(false);
                    if (fp.isInheritPackages() != null) {
                        fpConfig.setInheritPackages(fp.isInheritPackages());
                    }

                    if (fp.getIncludedDefaultConfig() != null) {
                        ConfigId configId = new ConfigId("standalone", fp.getIncludedDefaultConfig());
                        fpConfig.includeDefaultConfig(configId);
                        if (defaultConfig == null) {
                            defaultConfig = configId;
                            configBuilder = ConfigModel.
                                    builder(defaultConfig.getModel(), defaultConfig.getName());
                            // Must enforce the config name
                            configBuilder.setProperty("--server-config", "standalone.xml");
                        } else {
                            if (!defaultConfig.getName().equals(fp.getIncludedDefaultConfig())) {
                                throw new ProvisioningException("Feature-packs are not including the same default config");
                            }
                        }
                    }

                    if (!fp.getIncludedPackages().isEmpty()) {
                        for (String includedPackage : fp.getIncludedPackages()) {
                            fpConfig.includePackage(includedPackage);
                        }
                    }
                    if (!fp.getExcludedPackages().isEmpty()) {
                        for (String excludedPackage : fp.getExcludedPackages()) {
                            fpConfig.excludePackage(excludedPackage);
                        }
                    }

                    state.addFeaturePackDep(fpConfig.build());
                }
            }

            // We could be in a case where we are only excluding layers from the included default config.
            // So no layers included thanks to the <layers> element.
            if (!layers.isEmpty() || !excludedLayers.isEmpty()) {
                if (featurePackLocation == null && state == null) {
                    throw new ProvisioningException("No server feature-pack location to provision layers, you must set a feature-pack-location.");
                }
                if (Files.exists(provisioningFile)) {
                    getLog().warn("Layers defined in pom.xml override provisioning file located in " + provisioningFile);
                }
                // if not null means a default config has been included.
                if (configBuilder == null) {
                    configBuilder = ConfigModel.
                            builder("standalone", "standalone.xml");
                }

                for (String layer : layers) {
                    configBuilder.includeLayer(layer);
                }

                for (String layer : extraLayers) {
                    if (!layers.contains(layer)) {
                        configBuilder.includeLayer(layer);
                    }
                }

                for (String layer : excludedLayers) {
                    configBuilder.excludeLayer(layer);
                }
                // passive+ in all cases
                // For included default config not based on layers, default packages
                // must be included.
                if (pluginOptions.isEmpty()) {
                    pluginOptions = Collections.
                            singletonMap(Constants.OPTIONAL_PACKAGES, Constants.PASSIVE_PLUS);
                } else if (!pluginOptions.containsKey(Constants.OPTIONAL_PACKAGES)) {
                    pluginOptions.put(Constants.OPTIONAL_PACKAGES, Constants.PASSIVE_PLUS);
                }
                if (state == null) {
                    state = ProvisioningConfig.builder();
                    FeaturePackConfig dependency = FeaturePackConfig.
                            builder(FeaturePackLocation.fromString(featurePackLocation)).
                            setInheritPackages(false).setInheritConfigs(false).build();
                    state.addFeaturePackDep(dependency);
                }

                state.addConfig(configBuilder.build());
            }

            if (state == null) {
                if (Files.exists(provisioningFile)) {
                    config = ProvisioningXmlParser.parse(provisioningFile);
                } else {
                    if (featurePackLocation == null) {
                        throw new ProvisioningException("No server feature-pack location to provision microprofile standalone configuration, "
                                + "you must set a feature-pack-location.");
                    }
                    ConfigModel.Builder defaultConfigBuilder = null;
                    ConfigId defaultConfigId = getDefaultConfig();
                    if (!extraLayers.isEmpty()) {
                        defaultConfigBuilder = ConfigModel.
                                builder(defaultConfigId.getModel(), defaultConfigId.getName());
                        for (String layer : extraLayers) {
                            defaultConfigBuilder.includeLayer(layer);
                        }
                    }
                    if (pluginOptions.isEmpty()) {
                        pluginOptions = Collections.
                                singletonMap(Constants.OPTIONAL_PACKAGES, Constants.PASSIVE_PLUS);
                    } else if (!pluginOptions.containsKey(Constants.OPTIONAL_PACKAGES)) {
                        pluginOptions.put(Constants.OPTIONAL_PACKAGES, Constants.PASSIVE_PLUS);
                    }
                    FeaturePackConfig dependency = FeaturePackConfig.
                            builder(FeaturePackLocation.fromString(featurePackLocation)).
                            setInheritPackages(false).setInheritConfigs(false).includeDefaultConfig(defaultConfigId.getModel(), defaultConfigId.getName()).build();
                    ProvisioningConfig.Builder provBuilder = ProvisioningConfig.builder().addFeaturePackDep(dependency).addOptions(pluginOptions);
                    // Create a config to merge options to name the config standalone.xml
                    if (defaultConfigBuilder == null) {
                        defaultConfigBuilder = ConfigModel.builder(defaultConfigId.getModel(), defaultConfigId.getName());
                    }
                    defaultConfigBuilder.setProperty("--server-config", "standalone.xml");
                    provBuilder.addConfig(defaultConfigBuilder.build());
                    config = provBuilder.build();
                }
            } else {
                state.addOptions(pluginOptions);
                config = state.build();
            }

            IoUtils.recursiveDelete(home);
            getLog().info("Building server based on " + config.getFeaturePackDeps() + " galleon feature-packs");

            // store provisioning.xml
            try(FileWriter writer = new FileWriter(outputProvisioningFile.toFile())) {
                ProvisioningXmlWriter.getInstance().write(config, writer);
            }

            ProvisioningRuntime rt = pm.getRuntime(config);
            Artifact bootArtifact = null;
            for (FeaturePackRuntime fprt : rt.getFeaturePacks()) {
                if (fprt.getPackage(MODULE_ID_JAR_RUNTIME) != null) {
                    // We need to discover GAV of the associated boot.
                    Path artifactProps = fprt.getResource("wildfly/artifact-versions.properties");
                    final Map<String, String> propsMap = new HashMap<>();
                    try {
                        readProperties(artifactProps, propsMap);
                    } catch (Exception ex) {
                        throw new MojoExecutionException("Error reading artifact versions", ex);
                    }
                    for(Entry<String,String> entry : propsMap.entrySet()) {
                        String value = entry.getValue();
                        Artifact a = getArtifact(value);
                        if ( BOOT_ARTIFACT_ID.equals(a.getArtifactId())) {
                            // We got it.
                            getLog().info("Found boot artifact " + a + " in " + fprt.getFPID());
                            bootArtifact = a;
                            break;
                        }
                    }
                }
                // Lookup artifacts to retrieve the required dependencies for isolated CLI execution
                Path artifactProps = fprt.getResource("wildfly/artifact-versions.properties");
                final Map<String, String> propsMap = new HashMap<>();
                try {
                    readProperties(artifactProps, propsMap);
                } catch (Exception ex) {
                    throw new MojoExecutionException("Error reading artifact versions", ex);
                }
                for (Entry<String, String> entry : propsMap.entrySet()) {
                    String value = entry.getValue();
                    Artifact a = getArtifact(value);
                    if ("wildfly-cli".equals(a.getArtifactId())
                            && "org.wildfly.core".equals(a.getGroupId())) {
                        // We got it.
                        getLog().debug("Found cli artifact " + a + " in " + fprt.getFPID());
                        cliArtifacts.add(new DefaultArtifact(a.getGroupId(), a.getArtifactId(), a.getVersion(), "provided", JAR,
                                "client", new DefaultArtifactHandler(JAR)));
                        continue;
                    }
                    if ("wildfly-patching".equals(a.getArtifactId())
                            && "org.wildfly.core".equals(a.getGroupId())) {
                        // We got it.
                        getLog().debug("Found patching artifact " + a + " in " + fprt.getFPID());
                        cliArtifacts.add(a);
                        continue;
                    }
                    // All the following ones are patching required dependencies:
                    if ("wildfly-controller".equals(a.getArtifactId())
                            && "org.wildfly.core".equals(a.getGroupId())) {
                        // We got it.
                        getLog().debug("Found controller artifact " + a + " in " + fprt.getFPID());
                        cliArtifacts.add(a);
                        continue;
                    }
                    if ("wildfly-version".equals(a.getArtifactId())
                            && "org.wildfly.core".equals(a.getGroupId())) {
                        // We got it.
                        getLog().debug("Found version artifact " + a + " in " + fprt.getFPID());
                        cliArtifacts.add(a);
                    }
                    if ("vdx-core".equals(a.getArtifactId())
                            && "org.projectodd.vdx".equals(a.getGroupId())) {
                        // We got it.
                        getLog().debug("Found vdx-core artifact " + a + " in " + fprt.getFPID());
                        cliArtifacts.add(a);
                    }
                    // End patching dependencies.
                }
            }
            if (bootArtifact == null) {
                throw new ProvisioningException("Server doesn't support bootable jar packaging");
            }
            pm.provision(rt.getLayout());
            return bootArtifact;
        }
    }

    protected ConfigId getDefaultConfig() {
        return new ConfigId("standalone", "standalone-microprofile.xml");
    }

    static Artifact getArtifact(String str) {
        final String[] parts = str.split(":");
        final String groupId = parts[0];
        final String artifactId = parts[1];
        String version = parts[2];
        String classifier = parts[3];
        String extension = parts[4];

        return new DefaultArtifact(groupId, artifactId, version,
                            "provided", extension, classifier,
                            new DefaultArtifactHandler(extension));
    }

    private static void readProperties(Path propsFile, Map<String, String> propsMap) throws Exception {
        try (BufferedReader reader = Files.newBufferedReader(propsFile)) {
            String line = reader.readLine();
            while (line != null) {
                line = line.trim();
                if (!line.isEmpty() && line.charAt(0) != '#') {
                    final int i = line.indexOf('=');
                    if (i < 0) {
                        throw new Exception("Failed to parse property " + line + " from " + propsFile);
                    }
                    propsMap.put(line.substring(0, i), line.substring(i + 1));
                }
                line = reader.readLine();
            }
        }
    }

    private void deploy(List<String> commands) throws MojoExecutionException {
        if (hollowJar) {
            getLog().info("Hollow jar, No application deployment added to server.");
            return;
        }
        File f = validateProjectFile();

        String runtimeName = f.getName();
        if (project.getPackaging().equals(WAR) || runtimeName.endsWith(WAR)) {
            if (contextRoot) {
                runtimeName = "ROOT." + WAR;
            }
        }
        commands.add("deploy " + f.getAbsolutePath() + " --name=" + f.getName() + " --runtime-name=" + runtimeName);
    }

    private static void zipServer(Path home, Path contentDir) throws IOException {
        Path target = contentDir.resolve("wildfly.zip");
        ZipUtils.zip(home, target);
    }

    private void buildJar(Path contentDir, Path jarFile, Artifact artifact) throws MojoExecutionException, IOException {
        Path rtJarFile = resolveArtifact(artifact);
        ZipUtils.unzip(rtJarFile, contentDir);
        ZipUtils.zip(contentDir, jarFile);
    }

    public String retrievePluginVersion() throws PlexusConfigurationException, MojoExecutionException {
        InputStream is = getClass().getResourceAsStream("/META-INF/maven/plugin.xml");
        if (is == null) {
            throw new MojoExecutionException("Can't retrieve plugin descriptor");
        }
        PluginDescriptorBuilder builder = new PluginDescriptorBuilder();
        PluginDescriptor pluginDescriptor = builder.build(new InputStreamReader(is, StandardCharsets.UTF_8));
        return pluginDescriptor.getVersion();
    }

    public Path resolveArtifact(String groupId, String artifactId, String classifier, String version) throws UnsupportedEncodingException,
            PlexusConfigurationException, MojoExecutionException {
        return resolveArtifact(new DefaultArtifact(groupId, artifactId, version,
                "provided", JAR, classifier, new DefaultArtifactHandler(JAR)));
    }

    Path resolveArtifact(Artifact artifact) throws MojoExecutionException {
        MavenArtifact mavenArtifact = new MavenArtifact();
        mavenArtifact.setGroupId(artifact.getGroupId());
        mavenArtifact.setArtifactId(artifact.getArtifactId());
        mavenArtifact.setVersion(artifact.getVersion());
        mavenArtifact.setClassifier(artifact.getClassifier());
        mavenArtifact.setExtension(artifact.getType());
        try {
            artifactResolver.resolve(mavenArtifact);
            return mavenArtifact.getPath();
        } catch (MavenUniverseException ex) {
            throw new MojoExecutionException(ex.toString(), ex);
        }
    }

    private void attachJar(Path jarFile) {
        debug("Attaching bootable jar %s as a project artifact", jarFile);
        projectHelper.attachArtifact(project, JAR, BOOTABLE_SUFFIX, jarFile.toFile());
    }

    private void debug(String msg, Object... args) {
        if (getLog().isDebugEnabled()) {
            getLog().debug(String.format(msg, args));
        }
    }

    static void deleteDir(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    try {
                        Files.delete(file);
                    } catch (IOException ex) {
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException e)
                        throws IOException {
                    if (e != null) {
                        // directory iteration failed
                        throw e;
                    }
                    try {
                        Files.delete(dir);
                    } catch (IOException ex) {
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
        }
    }

    private Path resolveMaven(ArtifactCoordinate coordinate) throws MavenUniverseException {
        final MavenArtifact artifact = new MavenArtifact()
                .setGroupId(coordinate.getGroupId())
                .setArtifactId(coordinate.getArtifactId())
                .setVersion(coordinate.getVersion())
                .setExtension(coordinate.getExtension())
                .setClassifier(coordinate.getClassifier());
        artifactResolver.resolve(artifact);
        return artifact.getPath();
    }
}
