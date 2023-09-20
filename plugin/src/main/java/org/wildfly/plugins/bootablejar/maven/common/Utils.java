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
package org.wildfly.plugins.bootablejar.maven.common;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.api.GalleonBuilder;
import org.jboss.galleon.api.GalleonFeaturePackLayout;
import org.jboss.galleon.api.GalleonProvisioningLayout;
import org.jboss.galleon.api.Provisioning;
import org.jboss.galleon.config.ConfigId;
import org.jboss.galleon.api.config.GalleonFeaturePackConfig;
import org.jboss.galleon.api.config.GalleonProvisioningConfig;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.wildfly.plugins.bootablejar.BootableJarSupport;
import org.wildfly.plugins.bootablejar.maven.goals.BuildBootableJarMojo;
import static org.wildfly.plugins.bootablejar.maven.goals.AbstractBuildBootableJarMojo.STANDALONE;
import static org.wildfly.plugins.bootablejar.maven.goals.AbstractBuildBootableJarMojo.STANDALONE_XML;
/**
 * @author jdenise
 */
public class Utils {

    private static final String HEALTH = "health";
    private static final String MP_HEALTH = "microprofile-health";

    public static class ProvisioningSpecifics {

        private final boolean isMicroprofile;
        private final String healthLayer;

        ProvisioningSpecifics(Set<String> allLayers) {
            if (allLayers.contains(MP_HEALTH)) {
                healthLayer = MP_HEALTH;
                isMicroprofile = true;
            } else {
                if (allLayers.contains(HEALTH)) {
                    healthLayer = HEALTH;
                } else {
                    healthLayer = null;
                }
                isMicroprofile = false;
            }
        }

        public String getHealthLayer() {
            return healthLayer;
        }

        public ConfigId getDefaultConfig(boolean isCloud) {
            if (isCloud) {
                if (isMicroprofile) {
                    return new ConfigId(STANDALONE, "standalone-microprofile-ha.xml");
                } else {
                    return new ConfigId(STANDALONE, "standalone-ha.xml");
                }
            } else {
                if (isMicroprofile) {
                    return new ConfigId(STANDALONE, "standalone-microprofile.xml");
                } else {
                    return new ConfigId(STANDALONE, STANDALONE_XML);
                }
            }
        }
    }

    private static final Pattern WHITESPACE_IF_NOT_QUOTED = Pattern.compile("(\\S+\"[^\"]+\")|\\S+");

    public static String getBootableJarPath(String jarFileName, MavenProject project, String goal) throws MojoExecutionException {
        String jarName = jarFileName;
        if (jarName == null) {
            String finalName = project.getBuild().getFinalName();
            jarName = finalName + "-" + BootableJarSupport.BOOTABLE_SUFFIX + "." + BuildBootableJarMojo.JAR;
        }
        String path = project.getBuild().getDirectory() + File.separator + jarName;
        if (!Files.exists(Paths.get(path))) {
            throw new MojoExecutionException("Cannot " + goal + " without a bootable jar; please `mvn wildfly-jar:package` prior to invoking wildfly-jar:run from the command-line");
        }
        return path;
    }

    /**
     * Splits the arguments into a list. The arguments are split based on whitespace while ignoring whitespace that is
     * within quotes.
     *
     * @param arguments the arguments to split
     *
     * @return the list of the arguments
     */
    public static List<String> splitArguments(final CharSequence arguments) {
        final List<String> args = new ArrayList<>();
        final Matcher m = WHITESPACE_IF_NOT_QUOTED.matcher(arguments);
        while (m.find()) {
            final String value = m.group();
            if (!value.isEmpty()) {
                args.add(value);
            }
        }
        return args;
    }

    public static ProvisioningSpecifics getSpecifics(List<FeaturePack> fps, GalleonBuilder provider) throws ProvisioningException, IOException {
        return new ProvisioningSpecifics(getAllLayers(fps, provider));
    }

    private static Set<String> getAllLayers(List<FeaturePack> fps, GalleonBuilder provider) throws ProvisioningException, IOException {
        GalleonProvisioningConfig.Builder builder = GalleonProvisioningConfig.builder();
        for (FeaturePack fp : fps) {
            final FeaturePackLocation fpl;
            if (fp.getNormalizedPath() != null) {
                fpl = provider.addLocal(fp.getNormalizedPath(), false);
            } else if (fp.getGroupId() != null && fp.getArtifactId() != null) {
                String coords = fp.getMavenCoords();
                fpl = FeaturePackLocation.fromString(coords);
            } else {
                fpl = FeaturePackLocation.fromString(fp.getLocation());
            }
            builder.addFeaturePackDep(GalleonFeaturePackConfig.builder(fpl).build());
        }
        GalleonProvisioningConfig pConfig = builder.build();
        try (Provisioning pm = provider.newProvisioningBuilder(pConfig).build()) {
            return getAllLayers(pm, pConfig);
        }
    }

    private static Set<String> getAllLayers(Provisioning pm, GalleonProvisioningConfig pConfig)
            throws ProvisioningException, IOException {
        Set<String> layers = new HashSet<>();
        try (GalleonProvisioningLayout layout = pm.newProvisioningLayout(pConfig)) {
            for (GalleonFeaturePackLayout fp : layout.getOrderedFeaturePacks()) {
                for (ConfigId layer : fp.loadLayers()) {
                    layers.add(layer.getName());
                }
            }
        }
        return layers;
    }

    public static boolean isModularJVM() {
        final String javaSpecVersion = System.getProperty("java.specification.version");
        boolean modularJvm = false;
        if (javaSpecVersion != null) {
            final Matcher matcher = Pattern.compile("^(?:1\\.)?(\\d+)$").matcher(javaSpecVersion);
            if (matcher.find()) {
                modularJvm = Integer.parseInt(matcher.group(1)) >= 9;
            }
        }
        return modularJvm;
    }

}
