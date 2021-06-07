/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.config.ConfigId;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.layout.FeaturePackLayout;
import org.jboss.galleon.layout.ProvisioningLayout;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.wildfly.plugins.bootablejar.maven.common.FeaturePack;
import static org.wildfly.plugins.bootablejar.maven.goals.AbstractBuildBootableJarMojo.STANDALONE;
import static org.wildfly.plugins.bootablejar.maven.goals.AbstractBuildBootableJarMojo.STANDALONE_XML;

/**
 *
 * @author jdenise
 */
public class FeaturePacksUtil {

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

        ConfigId getDefaultConfig(boolean isCloud) {
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

        String getHealthLayer() {
            return healthLayer;
        }
    }

    static ProvisioningSpecifics getSpecifics(List<FeaturePack> fps, ProvisioningManager pm) throws ProvisioningException, IOException {
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
