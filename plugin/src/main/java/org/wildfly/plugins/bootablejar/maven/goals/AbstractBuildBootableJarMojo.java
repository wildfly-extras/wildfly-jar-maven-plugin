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
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.artifact.ArtifactCoordinate;
import org.apache.maven.shared.artifact.resolve.ArtifactResolver;
import org.apache.maven.shared.artifact.resolve.ArtifactResolverException;
import org.apache.maven.shared.artifact.resolve.ArtifactResult;
import org.codehaus.plexus.configuration.PlexusConfigurationException;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.impl.CommandContextConfiguration;
import org.jboss.galleon.Constants;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.config.ConfigModel;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.maven.plugin.util.ConfigurationId;
import org.jboss.galleon.maven.plugin.util.FeaturePack;
import org.jboss.galleon.maven.plugin.util.MavenArtifactRepositoryManager;
import org.jboss.galleon.maven.plugin.util.MvnMessageWriter;
import org.jboss.galleon.repo.RepositoryArtifactResolver;
import org.jboss.galleon.runtime.FeaturePackRuntime;
import org.jboss.galleon.runtime.ProvisioningRuntime;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.jboss.galleon.util.IoUtils;
import org.jboss.galleon.util.ZipUtils;
import org.jboss.galleon.xml.ProvisioningXmlParser;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 * Build a bootable jar containing application and provisioned server
 *
 * @author jfdenise
 */
class AbstractBuildBootableJarMojo extends AbstractMojo {

    public static final String BOOTABLE_SUFFIX = "wildfly";
    public static final String JAR = "jar";
    public static final String WAR = "war";
    private static final String MODULE_ID_JAR_RUNTIME = "org.wildfly.bootable-jar";

    @Component
    RepositorySystem repoSystem;

    @Component
    ArtifactResolver artifactResolver;

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
     * Arbitrary Galleon options used when provisioning the server.
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
    @Parameter(alias = "exclude-layers", required = false)
    List<String> excludeLayers = Collections.emptyList();

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
    @Parameter(alias = "root-url-path", defaultValue = "true", property = "wildfly.bootable.root.url")
    boolean rootUrlPath;

    /**
     * The WildFly galleon feature-pack location to use if no provisioning.xml
     * file found. Can't be used in conjunction with feature-packs.
     */
    @Parameter(alias = "feature-pack-location", required = false,
            property = "wildfly.bootable.fpl")
    String featurePackLocation;

    /**
     * Path to JBoss CLI scripts to execute once the server is provisioned and
     * application is deployed to the server.
     */
    @Parameter(alias = "cli-script-files")
    List<String> cliScriptFiles = Collections.emptyList();

    /**
     * Path to a JBoss CLI script properties file.
     */
    @Parameter(alias = "cli-properties-file")
    String propertiesFile;

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
     * By default the generated jar is ${project.build.finalName}-wildfly.jar
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

    private Set<String> extraLayers = new HashSet<>();

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

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
        Path contentRoot = Paths.get(project.getBuild().getDirectory()).resolve("bootable-jar-build-artifacts");
        if (Files.exists(contentRoot)) {
            deleteDir(contentRoot);
        }
        Path jarFile = Paths.get(project.getBuild().getDirectory()).resolve(outputFileName);
        IoUtils.recursiveDelete(contentRoot);

        Path wildflyDir = contentRoot.resolve("wildfly");
        Path contentDir = contentRoot.resolve("jar-content");
        try {
            Files.createDirectories(contentRoot);
            Files.createDirectories(contentDir);
            Files.deleteIfExists(jarFile);
        } catch (IOException ex) {
            throw new MojoExecutionException("Packaging wildfly failed", ex);
        }
        String bootVersion = null;
        try {
            bootVersion = provisionServer(wildflyDir);
        } catch (ProvisioningException ex) {
            throw new MojoExecutionException("Provisioning failed", ex);
        }
        try {
            copyExtraContent(wildflyDir);
            List<String> commands = new ArrayList<>();
            deploy(commands);
            userScripts(commands);
            configureCli(commands);
            if (!commands.isEmpty()) {
                executeCliScript(wildflyDir, commands);
            }
            cleanupServer(wildflyDir);
            zipServer(wildflyDir, contentDir);
            buildJar(contentDir, jarFile, bootVersion);
        } catch (Exception ex) {
            throw new MojoExecutionException("Packaging wildfly failed", ex);
        }

        attachJar(jarFile);
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
            if (rootUrlPath) {
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

    private void userScripts(List<String> commands) throws Exception {
        for (String path : cliScriptFiles) {
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
    }

    private void executeCliScript(Path jbossHome, List<String> commands) throws Exception {
        Properties props = null;
        if (propertiesFile != null) {
            props = loadProperties();
        }
        try {
            processCLI(jbossHome, commands);
        } finally {
            if (props != null) {
                for (String key : props.stringPropertyNames()) {
                    WildFlySecurityManager.clearPropertyPrivileged(key);
                }
            }
        }
    }

    private void processCLI(Path jbossHome, List<String> commands) throws Exception {
        getLog().info("Executing CLI scripts.");
        Level level = disableLog();
        Path config = jbossHome.resolve("bin").resolve("jboss-cli.xml");
        String origConfig = System.getProperty("jboss.cli.config");
        if (Files.exists(config)) {
            System.setProperty("jboss.cli.config", config.toString());
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Exception originalException = null;
        try {
            CommandContextConfiguration.Builder builder = new CommandContextConfiguration.Builder();
            builder.setEchoCommand(true);
            builder.setResolveParameterValues(true);
            builder.setConsoleOutput(out);
            CommandContext cmdCtx = CommandContextFactory.getInstance().newCommandContext(builder.build());

            try {
                cmdCtx.handle("embed-server --jboss-home=" + jbossHome + " --std-out=discard");
                for (String line : commands) {
                    cmdCtx.handle(line.trim());
                }
            } catch (Exception ex) {
                originalException = ex;
            } finally {
                try {
                    cmdCtx.handle("stop-embedded-server");
                } catch (Exception ex2) {
                    if (originalException != null) {
                        ex2.addSuppressed(originalException);
                    }
                    throw ex2;
                }
            }
            if (originalException != null) {
                throw originalException;
            }
        } finally {
            enableLog(level);
            if (origConfig != null) {
                System.setProperty("jboss.cli.config", origConfig);
            }
            if (originalException != null) {
                getLog().error("Error executing CLI script " + originalException.getLocalizedMessage());
                getLog().error(out.toString());
            }

        }
        getLog().info("CLI scripts execution done.");
    }

    private Level disableLog() {
        Logger l = Logger.getLogger("");
        Level level = l.getLevel();
        // Only disable logging if debug is not ebnabled.
        if (!getLog().isDebugEnabled()) {
            l.setLevel(Level.OFF);
        }
        return level;
    }

    private void enableLog(Level level) {
        Logger l = Logger.getLogger("");
        l.setLevel(level);
    }

    private Properties loadProperties() throws Exception {
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
        InputStreamReader inputStreamReader = null;
        try {
            inputStreamReader = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8);
            props.load(inputStreamReader);
        } catch (java.io.IOException e) {
            throw new Exception("Failed to load properties from " + propertiesFile + ": " + e.getLocalizedMessage());
        } finally {
            if (inputStreamReader != null) {
                try {
                    inputStreamReader.close();
                } catch (java.io.IOException e) {
                }
            }
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
        return Paths.get(project.getBasedir().getAbsolutePath()).resolve("galleon").resolve("provisioning.xml");
    }

    protected boolean hasLayers() {
        return !layers.isEmpty();
    }

    protected List<String> getLayers() {
        return layers;
    }

    protected List<String> getExcludedLayers() {
        return excludeLayers;
    }

    private String provisionServer(Path home) throws ProvisioningException, MojoExecutionException {
        final RepositoryArtifactResolver artifactResolver = offline ? new MavenArtifactRepositoryManager(repoSystem, repoSession)
                : new MavenArtifactRepositoryManager(repoSystem, repoSession, repositories);

        final Path provisioningFile = getProvisioningFile();
        ProvisioningConfig.Builder state = null;
        ProvisioningConfig config;
        try (ProvisioningManager pm = ProvisioningManager.builder().addArtifactResolver(artifactResolver)
                .setInstallationHome(home)
                .setMessageWriter(new MvnMessageWriter(getLog()))
                .setLogTime(logTime)
                .setRecordState(recordState)
                .build()) {

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
                        Path path = resolveMaven(fp, (MavenRepoManager) artifactResolver);
                        fpl = pm.getLayoutFactory().addLocal(path, false);
                    } else {
                        fpl = FeaturePackLocation.fromString(fp.getLocation());
                    }

                    final FeaturePackConfig.Builder fpConfig = fp.isTransitive() ? FeaturePackConfig.transitiveBuilder(fpl)
                            : FeaturePackConfig.builder(fpl);
                    if (fp.isInheritConfigs() != null) {
                        fpConfig.setInheritConfigs(fp.isInheritConfigs());
                    }
                    if (fp.isInheritPackages() != null) {
                        fpConfig.setInheritPackages(fp.isInheritPackages());
                    }

                    if (!fp.getExcludedConfigs().isEmpty()) {
                        for (ConfigurationId configId : fp.getExcludedConfigs()) {
                            if (configId.isModelOnly()) {
                                fpConfig.excludeConfigModel(configId.getId().getModel());
                            } else {
                                fpConfig.excludeDefaultConfig(configId.getId());
                            }
                        }
                    }
                    if (!fp.getIncludedConfigs().isEmpty()) {
                        for (ConfigurationId configId : fp.getIncludedConfigs()) {
                            if (configId.isModelOnly()) {
                                fpConfig.includeConfigModel(configId.getId().getModel());
                            } else {
                                fpConfig.includeDefaultConfig(configId.getId());
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

            if (!layers.isEmpty()) {
                if (featurePackLocation == null && state == null) {
                    throw new ProvisioningException("No server feature-pack location to provision layers, you must set a feature-pack-location.");
                }
                if (Files.exists(provisioningFile)) {
                    getLog().warn("Layers defined in pom.xml override provisioning file located in " + provisioningFile);
                }
                ConfigModel.Builder configBuilder = ConfigModel.
                        builder("standalone", "standalone.xml");

                for (String layer : layers) {
                    configBuilder.includeLayer(layer);
                }

                for (String layer : extraLayers) {
                    if (!layers.contains(layer)) {
                        configBuilder.includeLayer(layer);
                    }
                }

                for (String layer : excludeLayers) {
                    configBuilder.excludeLayer(layer);
                }
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
                        throw new ProvisioningException("No server feature-pack location to provision standalone configuration, you must set a feature-pack-location.");
                    }
                    ConfigModel.Builder configBuilder = null;
                    if (!extraLayers.isEmpty()) {
                        configBuilder = ConfigModel.
                                builder("standalone", "standalone.xml");
                        for (String layer : extraLayers) {
                            configBuilder.includeLayer(layer);
                        }
                    }
                    FeaturePackConfig dependency = FeaturePackConfig.
                            builder(FeaturePackLocation.fromString(featurePackLocation)).
                            setInheritPackages(true).setInheritConfigs(false).includeDefaultConfig("standalone", "standalone.xml").build();
                    ProvisioningConfig.Builder provBuilder = ProvisioningConfig.builder().addFeaturePackDep(dependency).addOptions(pluginOptions);
                    if (configBuilder != null) {
                        provBuilder.addConfig(configBuilder.build());
                    }
                    config = provBuilder.build();
                }
            } else {
                state.addOptions(pluginOptions);
                config = state.build();
            }

            IoUtils.recursiveDelete(home);
            getLog().info("Building server based on " + config.getFeaturePackDeps() + " galleon feature-packs");

            ProvisioningRuntime rt = pm.getRuntime(config);
            String version = null;
            for (FeaturePackRuntime fprt : rt.getFeaturePacks()) {
                if (fprt.getPackage(MODULE_ID_JAR_RUNTIME) != null) {
                    version = fprt.getFPID().getBuild();
                    break;
                }
            }
            if (version == null) {
                throw new ProvisioningException("Server doesn't support bootable jar packaging");
            }
            pm.provision(rt.getLayout());
            return version;
        }
    }

    private void deploy(List<String> commands) throws IOException, MojoExecutionException {
        if (hollowJar) {
            getLog().info("Hollow jar, No application deployment added to server.");
            return;
        }
        File f = validateProjectFile();

        String runtimeName = f.getName();
        if (project.getPackaging().equals(WAR) || runtimeName.endsWith(WAR)) {
            if (rootUrlPath) {
                runtimeName = "ROOT." + WAR;
            }
        }
        commands.add("deploy " + f.getAbsolutePath() + " --name=" + f.getName() + " --runtime-name=" + runtimeName);
    }

    private static void zipServer(Path home, Path contentDir) throws IOException {
        Path target = contentDir.resolve("wildfly.zip");
        ZipUtils.zip(home, target);
    }

    private void buildJar(Path contentDir, Path jarFile, String bootVersion) throws MojoExecutionException, IOException {
        try {
            Path rtJarFile = resolveBoot(bootVersion);
            ZipUtils.unzip(rtJarFile, contentDir);
            ZipUtils.zip(contentDir, jarFile);
        } catch (PlexusConfigurationException | UnsupportedEncodingException e) {
            throw new MojoExecutionException("Failed to resolve rt jar ", e);
        }
    }

    private Path resolveBoot(String version) throws UnsupportedEncodingException,
            PlexusConfigurationException, MojoExecutionException {
        final ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
        buildingRequest.setLocalRepository(session.getLocalRepository());
        buildingRequest.setRemoteRepositories(project.getRemoteArtifactRepositories());

        try {
            final ArtifactResult result = artifactResolver.resolveArtifact(buildingRequest,
                    new DefaultArtifact("org.wildfly.core", "wildfly-jar-boot", version,
                            "provided", JAR, null,
                            new DefaultArtifactHandler(JAR)));
            return result.getArtifact().getFile().toPath();
        } catch (ArtifactResolverException ex) {
            throw new MojoExecutionException("Can't resolve boot artifact, server depends on wildfly-core "
                    + version + " that doesn't support bootable jar packaging");
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

    private Path resolveMaven(ArtifactCoordinate coordinate, MavenRepoManager resolver) throws MavenUniverseException {
        final MavenArtifact artifact = new MavenArtifact()
                .setGroupId(coordinate.getGroupId())
                .setArtifactId(coordinate.getArtifactId())
                .setVersion(coordinate.getVersion())
                .setExtension(coordinate.getExtension())
                .setClassifier(coordinate.getClassifier());
        resolver.resolve(artifact);
        return artifact.getPath();
    }
}
