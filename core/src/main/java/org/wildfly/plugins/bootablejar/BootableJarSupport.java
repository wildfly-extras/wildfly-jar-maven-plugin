/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.plugins.bootablejar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.jboss.galleon.MessageWriter;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.runtime.FeaturePackRuntime;
import org.jboss.galleon.runtime.ProvisioningRuntime;
import org.jboss.galleon.universe.maven.MavenArtifact;
import org.jboss.galleon.universe.maven.MavenUniverseException;
import org.jboss.galleon.universe.maven.repo.MavenRepoManager;
import org.jboss.galleon.util.IoUtils;
import org.jboss.galleon.util.ZipUtils;

/**
 *
 * @author jdenise
 */
public class BootableJarSupport {

    public static final String BOOTABLE_SUFFIX = "bootable";

    public static final String JBOSS_MODULES_GROUP_ID = "org.jboss.modules";
    public static final String JBOSS_MODULES_ARTIFACT_ID = "jboss-modules";

    private static final String MODULE_ID_JAR_RUNTIME = "org.wildfly.bootable-jar";

    private static final String BOOT_ARTIFACT_ID = "wildfly-jar-boot";
    public static final String WILDFLY_ARTIFACT_VERSIONS_RESOURCE_PATH = "wildfly/artifact-versions.properties";

    /**
     * Package a wildfly server as a bootable JAR.
     */
    public static void packageBootableJar(Path targetJarFile, Path target,
            ProvisioningConfig config, Path serverHome, MavenRepoManager resolver,
            MessageWriter writer, ArtifactLog log, String bootableJARVersion) throws Exception {
        Path contentRootDir = target.resolve("bootable-jar-build-artifacts");
        if (Files.exists(contentRootDir)) {
            IoUtils.recursiveDelete(contentRootDir);
        }
        Files.createDirectories(contentRootDir);
        try {
            zipServer(serverHome, contentRootDir);
            ScannedArtifacts bootable;
            Path emptyHome = contentRootDir.resolve("tmp-home");
            Files.createDirectories(emptyHome);
            try (ProvisioningManager pm = ProvisioningManager.builder().addArtifactResolver(resolver)
                    .setInstallationHome(emptyHome)
                    .setMessageWriter(writer)
                    .build()) {
                bootable = scanArtifacts(pm, config, log);
            }
            // Extract the cloud extension if cloud execution context.
            if (bootableJARVersion != null) {
                try (InputStream stream = BootableJarSupport.class.getClassLoader().getResourceAsStream("config.properties")) {
                    Properties props = new Properties();
                    props.load(stream);
                    unzipCloudExtension(contentRootDir, bootableJARVersion, resolver);
                    // Needed by extension
                    Path marker = contentRootDir.resolve("openshift.properties");
                    Files.createFile(marker);
                }
            }
            buildJar(contentRootDir, targetJarFile, bootable, resolver);
        } finally {
            IoUtils.recursiveDelete(contentRootDir);
        }
    }

    public static void unzipCloudExtension(Path contentDir, String version, MavenRepoManager resolver) throws MavenUniverseException, IOException {
        MavenArtifact ma = new MavenArtifact();
        ma.setGroupId("org.wildfly.plugins");
        ma.setArtifactId("wildfly-jar-cloud-extension");
        ma.setExtension("jar");
        ma.setVersion(version);
        resolver.resolve(ma);
        ZipUtils.unzip(ma.getPath(), contentDir);
    }

    public static void zipServer(Path home, Path contentDir) throws IOException {
        cleanupServer(home);
        Path target = contentDir.resolve("wildfly.zip");
        zip(home, target);
    }

    private static void cleanupServer(Path jbossHome) throws IOException {
        Path history = jbossHome.resolve("standalone").resolve("configuration").resolve("standalone_xml_history");
        IoUtils.recursiveDelete(history);
        Files.deleteIfExists(jbossHome.resolve("README.txt"));
    }

    public static void zip(Path contentDir, Path jarFile) throws IOException {
        ZipUtils.zip(contentDir, jarFile);
    }

    public static ScannedArtifacts scanArtifacts(ProvisioningManager pm, ProvisioningConfig config, ArtifactLog log) throws Exception {
        Set<MavenArtifact> cliArtifacts = new HashSet<>();
        MavenArtifact jbossModules = null;
        MavenArtifact bootArtifact = null;
        try (ProvisioningRuntime rt = pm.getRuntime(config)) {
            for (FeaturePackRuntime fprt : rt.getFeaturePacks()) {
                if (fprt.getPackage(MODULE_ID_JAR_RUNTIME) != null) {
                    // We need to discover GAV of the associated boot.
                    Path artifactProps = fprt.getResource(WILDFLY_ARTIFACT_VERSIONS_RESOURCE_PATH);
                    final Map<String, String> propsMap = new HashMap<>();
                    try {
                        readProperties(artifactProps, propsMap);
                    } catch (Exception ex) {
                        throw new Exception("Error reading artifact versions", ex);
                    }
                    for (Map.Entry<String, String> entry : propsMap.entrySet()) {
                        String value = entry.getValue();
                        MavenArtifact a = getArtifact(value);
                        if (BOOT_ARTIFACT_ID.equals(a.getArtifactId())) {
                            // We got it.
                            log.info(fprt.getFPID(), a);
                            bootArtifact = a;
                            break;
                        }
                    }
                }
                // Lookup artifacts to retrieve the required dependencies for isolated CLI execution
                Path artifactProps = fprt.getResource(WILDFLY_ARTIFACT_VERSIONS_RESOURCE_PATH);
                final Map<String, String> propsMap = new HashMap<>();
                try {
                    readProperties(artifactProps, propsMap);
                } catch (Exception ex) {
                    throw new Exception("Error reading artifact versions", ex);
                }
                for (Map.Entry<String, String> entry : propsMap.entrySet()) {
                    String value = entry.getValue();
                    MavenArtifact a = getArtifact(value);
                    if ("wildfly-cli".equals(a.getArtifactId())
                            && "org.wildfly.core".equals(a.getGroupId())) {
                        // We got it.
                        a.setClassifier("client");
                        log.debug(fprt.getFPID(), a);
                        cliArtifacts.add(a);
                        continue;
                    }
                    if (JBOSS_MODULES_ARTIFACT_ID.equals(a.getArtifactId())
                            && JBOSS_MODULES_GROUP_ID.equals(a.getGroupId())) {
                        jbossModules = a;
                    }
                }
            }
        }
        if (bootArtifact == null) {
            throw new ProvisioningException("Server doesn't support bootable jar packaging");
        }
        if (jbossModules == null) {
            throw new ProvisioningException("JBoss Modules not found in dependency, can't create a Bootable JAR");
        }
        return new ScannedArtifacts(bootArtifact, jbossModules, cliArtifacts);
    }

    public static void buildJar(Path contentDir, Path jarFile, ScannedArtifacts bootable, MavenRepoManager resolver) throws Exception {
        resolver.resolve(bootable.getBoot());
        Path rtJarFile = bootable.getBoot().getPath();
        resolver.resolve(bootable.getJbossModules());
        Path jbossModulesFile = bootable.getJbossModules().getPath();
        ZipUtils.unzip(jbossModulesFile, contentDir);
        ZipUtils.unzip(rtJarFile, contentDir);
        zip(contentDir, jarFile);
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

    static MavenArtifact getArtifact(String str) {
        final String[] parts = str.split(":");
        final String groupId = parts[0];
        final String artifactId = parts[1];
        String version = parts[2];
        String classifier = parts[3];
        String extension = parts[4];

        MavenArtifact ma = new MavenArtifact();
        ma.setGroupId(groupId);
        ma.setArtifactId(artifactId);
        ma.setVersion(version);
        ma.setClassifier(classifier);
        ma.setExtension(extension);
        return ma;
    }
}
