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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugin.descriptor.PluginDescriptorBuilder;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
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
import org.jboss.as.cli.CommandLineException;
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

/**
 * Build an uberjar containing application and provisioned server
 *
 * @author jfdenise
 */
@Mojo(name = "package", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PACKAGE)
public final class BuildUberJarMojo extends AbstractMojo {

    public static final String UBERJAR_SUFFIX = "wildfly-uberjar";
    public static final String JAR = "jar";
    public static final String WAR = "war";

    @Component
    private RepositorySystem repoSystem;

    @Component
    private ArtifactResolver artifactResolver;

    @Component
    private MavenProjectHelper projectHelper;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    private List<RemoteRepository> repositories;

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    /**
     * Arbitrary Galleon options used when provisioning the server.
     */
    @Parameter(alias = "plugin-options", required = false)
    private Map<String, String> pluginOptions = Collections.emptyMap();

    /**
     * Whether to use offline mode when the plugin resolves an artifact. In
     * offline mode the plugin will only use the local Maven repository for an
     * artifact resolution.
     */
    @Parameter(alias = "offline", defaultValue = "false")
    private boolean offline;

    /**
     * Whether to log provisioning time at the end
     */
    @Parameter(alias = "log-time", defaultValue = "false")
    private boolean logTime;

    /**
     * A list of galleon layers to provision.
     */
    @Parameter(alias = "layers", required = false)
    private List<String> layers = Collections.emptyList();

    /**
     * A list of galleon layers to exclude.
     */
    @Parameter(alias = "exclude-layers", required = false)
    private List<String> excludeLayers = Collections.emptyList();

    /**
     * Whether to record provisioned state in .galleon directory.
     */
    @Parameter(alias = "record-state", defaultValue = "false")
    private boolean recordState;

    @Parameter(defaultValue = "${project.build.directory}")
    private String projectBuildDir;

    /**
     * To make the war registered under root resource path ('/').
     */
    @Parameter(alias = "root-url-path", defaultValue = "true")
    private boolean rootUrlPath;

    /**
     * The WildFly galleon feature-pack location to use if no provisioning.xml
     * file found.
     */
    @Parameter(alias = "default-feature-pack-location", defaultValue = "wildfly@maven(org.jboss.universe:community-universe)")
    private String defaultFpl;

    /**
     * Path to a JBoss CLI script to execute once the server is provisioned and
     * application installed in server.
     */
    @Parameter(alias = "cli-script-file")
    private String cliScriptFile;

    /**
     * Hollow jar. Create an uberjar that doesn't contain application.
     */
    @Parameter(alias = "hollow-jar")
    private boolean hollow;

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
        validateProjectFile();
        Path contentRoot = Paths.get(project.getBuild().getDirectory()).resolve("uberjar-build-artifacts");
        if (Files.exists(contentRoot)) {
            deleteDir(contentRoot);
        }
        Path jarFile = Paths.get(project.getBuild().getDirectory()).resolve(this.project.getBuild().getFinalName() + "-" + UBERJAR_SUFFIX + "." + JAR);
        IoUtils.recursiveDelete(contentRoot);

        Path wildflyDir = contentRoot.resolve("wildfly");
        Path contentDir = contentRoot.resolve("jar-content");
        try {
            Files.createDirectory(contentRoot);
            Files.createDirectory(contentDir);
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
            copyProjectFile(wildflyDir);
            if (cliScriptFile != null) {
                executeCli(wildflyDir);
            }
            zipServer(wildflyDir, contentDir);
            buildJar(contentDir, jarFile);
        } catch (IOException ex) {
            throw new MojoExecutionException("Packaging wildfly failed", ex);
        }

        attachJar(jarFile);
    }

    private File validateProjectFile() throws MojoExecutionException {
        File f = getProjectFile();
        if (f == null && !hollow) {
            throw new MojoExecutionException("Cannot package without a primary artifact; please `mvn package` prior to invoking wildfly-uberjar:build-uber-jar from the command-line");
        }
        return f;
    }

    private void executeCli(Path jbossHome) {
        File f = new File(cliScriptFile);
        if (!f.exists()) {
            throw new RuntimeException("Cli script file doesn't exist");
        }

        processFile(jbossHome, f);
    }

    private void processFile(Path jbossHome, File file) {

        try {
            getLog().info("Executing CLI script " + file);
            CommandContext cmdCtx = CommandContextFactory.getInstance().newCommandContext();
            try ( BufferedReader reader = new BufferedReader(new FileReader(file))) {
                cmdCtx.handle("embed-server --jboss-home=" + jbossHome + " --std-out=echo");
                String line = reader.readLine();
                while (line != null) {
                    cmdCtx.handle(line.trim());
                    line = reader.readLine();
                }
            } finally {
                cmdCtx.handle("stop-embedded-server");
            }
        } catch (IOException | CommandLineException ex) {
            throw new RuntimeException("Failure executing CLI script", ex);
        }
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
                    builder(FeaturePackLocation.fromString(defaultFpl)).
                    setInheritPackages(false).setInheritConfigs(false).build();
            config = ProvisioningConfig.builder().addFeaturePackDep(dependency).addConfig(configBuilder.build()).build();
        } else {
            if (Files.exists(provisioningFile)) {
                config = ProvisioningXmlParser.parse(provisioningFile);
            } else {
                FeaturePackConfig dependency = FeaturePackConfig.
                        builder(FeaturePackLocation.fromString(defaultFpl)).
                        setInheritPackages(true).setInheritConfigs(false).includeDefaultConfig("standalone", "standalone.xml").build();
                config = ProvisioningConfig.builder().addFeaturePackDep(dependency).build();
            }
        }
        IoUtils.recursiveDelete(home);
        try ( ProvisioningManager pm = ProvisioningManager.builder().addArtifactResolver(artifactResolver)
                .setInstallationHome(home)
                .setMessageWriter(new MvnMessageWriter(getLog()))
                .setLogTime(logTime)
                .setRecordState(recordState)
                .build()) {
            pm.provision(config, pluginOptions);
        }
    }

    private void copyProjectFile(Path wildflyDir) throws IOException, MojoExecutionException {
        if (hollow) {
            getLog().info("Hollow jar, No application deployment added to server.");
            return;
        }
        File f = validateProjectFile();

        String fileName = f.getName();
        if (project.getPackaging().equals(WAR)) {
            if (rootUrlPath) {
                fileName = "ROOT." + WAR;
            }
        }
        Files.copy(f.toPath(), wildflyDir.resolve("standalone/deployments/" + fileName));
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
                new DefaultArtifact("org.wildfly.plugins", "wildfly-uberjar-runtime", retrieveRuntimeVersion(),
                        "provided", JAR, null,
                        new DefaultArtifactHandler(JAR)));
        return result.getArtifact().getFile().toPath();
    }

    private void attachJar(Path jarFile) {
        debug("Attaching uberjar %s as a project artifact", jarFile);
        projectHelper.attachArtifact(project, JAR, UBERJAR_SUFFIX, jarFile.toFile());
    }

    private void debug(String msg, Object... args) {
        if (getLog().isDebugEnabled()) {
            getLog().debug(String.format(msg, args));
        }
    }

    private static void deleteDir(Path root) {
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
