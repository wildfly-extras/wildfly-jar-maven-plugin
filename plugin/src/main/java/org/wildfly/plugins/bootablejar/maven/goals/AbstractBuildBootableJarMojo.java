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
package org.wildfly.plugins.bootablejar.maven.goals;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
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
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectBuildingRequest;
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
import org.jboss.galleon.maven.plugin.util.MavenArtifactRepositoryManager;
import org.jboss.galleon.maven.plugin.util.MvnMessageWriter;
import org.jboss.galleon.repo.RepositoryArtifactResolver;
import org.jboss.galleon.universe.FeaturePackLocation;
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
     * A list of galleon layers to provision.
     */
    @Parameter(alias = "layers", required = false)
    List<String> layers = Collections.emptyList();

    /**
     * A list of galleon layers to exclude.
     */
    @Parameter(alias = "exclude-layers", required = false)
    List<String> excludeLayers = Collections.emptyList();

    /**
     * Whether to record provisioned state in .galleon directory.
     */
    @Parameter(alias = "record-state", defaultValue = "false")
    boolean recordState;

    @Parameter(defaultValue = "${project.build.directory}")
    String projectBuildDir;

    /**
     * To make the war registered under root resource path ('/').
     */
    @Parameter(alias = "root-url-path", defaultValue = "true", property = "wildfly.bootable.root.url")
    boolean rootUrlPath;

    /**
     * The WildFly galleon feature-pack location to use if no provisioning.xml
     * file found.
     */
    @Parameter(alias = "feature-pack-location", required = false,
            property = "wildfly.bootable.fpl")
    String featurePackLocation;

    /**
     * Path to JBoss CLI scripts to execute once the server is provisioned and
     * application installed in server.
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
     * By default the generated jar is
     * ${project.build.finalName}-wildfly.jar
     */
    @Parameter(alias = "output-file-name", property = "wildfly.bootable.package.output.file.name")
    String outputFileName;

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
        try {
            provisionServer(wildflyDir);
        } catch (ProvisioningException ex) {
            throw new MojoExecutionException("Provisioning failed", ex);
        }
        try {
            List<String> commands = new ArrayList<>();
            deploy(commands);
            userScripts(commands);
            configureCli(commands);
            executeCliScript(wildflyDir, commands);
            cleanupServer(wildflyDir);
            zipServer(wildflyDir, contentDir);
            buildJar(contentDir, jarFile);
        } catch (Exception ex) {
            throw new MojoExecutionException("Packaging wildfly failed", ex);
        }

        attachJar(jarFile);
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
        IoUtils.recursiveDelete(jbossHome.resolve("bin"));
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
        CommandContextConfiguration.Builder builder = new CommandContextConfiguration.Builder();
        builder.setResolveParameterValues(true);
        CommandContext cmdCtx = CommandContextFactory.getInstance().newCommandContext(builder.build());
        Exception originalException = null;
        try {
            cmdCtx.handle("embed-server --jboss-home=" + jbossHome + " --std-out=echo");
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

    private void provisionServer(Path home) throws ProvisioningException {
        final RepositoryArtifactResolver artifactResolver = offline ? new MavenArtifactRepositoryManager(repoSystem, repoSession)
                : new MavenArtifactRepositoryManager(repoSystem, repoSession, repositories);

        final Path provisioningFile = Paths.get(project.getBasedir().getAbsolutePath()).resolve("galleon").resolve("provisioning.xml");
        ProvisioningConfig config;
        if (!layers.isEmpty() || !excludeLayers.isEmpty()) {
            if (featurePackLocation == null) {
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
            for (String layer : excludeLayers) {
                configBuilder.excludeLayer(layer);
            }
            if (pluginOptions.isEmpty()) {
                pluginOptions = Collections.
                        singletonMap(Constants.OPTIONAL_PACKAGES, Constants.PASSIVE_PLUS);
            } else if (!pluginOptions.containsKey(Constants.OPTIONAL_PACKAGES)) {
                pluginOptions.put(Constants.OPTIONAL_PACKAGES, Constants.PASSIVE_PLUS);
            }
            FeaturePackConfig dependency = FeaturePackConfig.
                    builder(FeaturePackLocation.fromString(featurePackLocation)).
                    setInheritPackages(false).setInheritConfigs(false).build();
            config = ProvisioningConfig.builder().addFeaturePackDep(dependency).addConfig(configBuilder.build()).build();
        } else {
            if (Files.exists(provisioningFile)) {
                config = ProvisioningXmlParser.parse(provisioningFile);
            } else {
                if (featurePackLocation == null) {
                    throw new ProvisioningException("No server feature-pack location to provision standalone configuration, you must set a feature-pack-location.");
                }
                FeaturePackConfig dependency = FeaturePackConfig.
                        builder(FeaturePackLocation.fromString(featurePackLocation)).
                        setInheritPackages(true).setInheritConfigs(false).includeDefaultConfig("standalone", "standalone.xml").build();
                config = ProvisioningConfig.builder().addFeaturePackDep(dependency).build();
            }
        }
        IoUtils.recursiveDelete(home);
        getLog().info("Building server based on " + config.getFeaturePackDeps() + " galleon feature-packs");
        try (ProvisioningManager pm = ProvisioningManager.builder().addArtifactResolver(artifactResolver)
                .setInstallationHome(home)
                .setMessageWriter(new MvnMessageWriter(getLog()))
                .setLogTime(logTime)
                .setRecordState(recordState)
                .build()) {
            pm.provision(config, pluginOptions);
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

    private String retrieveRuntimeVersion() throws UnsupportedEncodingException, PlexusConfigurationException, MojoExecutionException {
        InputStream is = getClass().getResourceAsStream("/META-INF/maven/plugin.xml");
        if (is == null) {
            throw new MojoExecutionException("Can't retrieve plugin descriptor");
        }
        PluginDescriptorBuilder builder = new PluginDescriptorBuilder();
        PluginDescriptor pluginDescriptor = builder.build(new InputStreamReader(is, "UTF-8"));
        return pluginDescriptor.getVersion();
    }

    private void buildJar(Path contentDir, Path jarFile) throws MojoExecutionException, IOException {
        try {
            Path rtJarFile = resolveRuntime();
            ZipUtils.unzip(rtJarFile, contentDir);
            ZipUtils.zip(contentDir, jarFile);
        } catch (PlexusConfigurationException | UnsupportedEncodingException | ArtifactResolverException e) {
            throw new MojoExecutionException("Failed to resolve rt jar ", e);
        }
    }

    private Path resolveRuntime() throws ArtifactResolverException, UnsupportedEncodingException,
            PlexusConfigurationException, MojoExecutionException {
        final ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest(session.getProjectBuildingRequest());
        buildingRequest.setLocalRepository(session.getLocalRepository());
        buildingRequest.setRemoteRepositories(project.getRemoteArtifactRepositories());

        final ArtifactResult result = artifactResolver.resolveArtifact(buildingRequest,
                new DefaultArtifact("org.wildfly.plugins", "wildfly-jar-runtime", retrieveRuntimeVersion(),
                        "provided", JAR, null,
                        new DefaultArtifactHandler(JAR)));
        return result.getArtifact().getFile().toPath();
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

}
