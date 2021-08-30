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
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.config.ConfigId;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.layout.FeaturePackLayout;
import org.jboss.galleon.layout.ProvisioningLayout;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.wildfly.plugins.bootablejar.maven.goals.BuildBootableJarMojo;

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
    }

    private static final Pattern WHITESPACE_IF_NOT_QUOTED = Pattern.compile("(\\S+\"[^\"]+\")|\\S+");

    public static String getBootableJarPath(String jarFileName, MavenProject project, String goal) throws MojoExecutionException {
        String jarName = jarFileName;
        if (jarName == null) {
            String finalName = project.getBuild().getFinalName();
            jarName = finalName + "-" + BuildBootableJarMojo.BOOTABLE_SUFFIX + "." + BuildBootableJarMojo.JAR;
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

    public static ProvisioningSpecifics getSpecifics(List<FeaturePack> fps, ProvisioningManager pm) throws ProvisioningException, IOException {
        return new ProvisioningSpecifics(getAllLayers(fps, pm));
    }

    private static Set<String> getAllLayers(List<FeaturePack> fps, ProvisioningManager pm) throws ProvisioningException, IOException {
        Set<String> allLayers = new HashSet<>();
        for (FeaturePack fp : fps) {
            final FeaturePackLocation fpl;
            if (fp.getNormalizedPath() != null) {
                fpl = pm.getLayoutFactory().addLocal(fp.getNormalizedPath(), false);
            } else if (fp.getGroupId() != null && fp.getArtifactId() != null) {
                String coords = fp.getMavenCoords();
                fpl = FeaturePackLocation.fromString(coords);
            } else {
                fpl = FeaturePackLocation.fromString(fp.getLocation());
            }
            ProvisioningConfig pConfig = ProvisioningConfig.builder().
                    addFeaturePackDep(FeaturePackConfig.builder(fpl).build()).build();
            try (ProvisioningLayout<FeaturePackLayout> layout = pm.
                    getLayoutFactory().newConfigLayout(pConfig)) {
                allLayers.addAll(getAllLayers(layout));
            }
        }
        return allLayers;
    }

    private static Set<String> getAllLayers(ProvisioningLayout<FeaturePackLayout> pLayout)
            throws ProvisioningException, IOException {
        Set<String> layers = new HashSet<>();
        for (FeaturePackLayout fp : pLayout.getOrderedFeaturePacks()) {
            for (ConfigId layer : fp.loadLayers()) {
                layers.add(layer.getName());
            }
        }
        return layers;
    }
}
