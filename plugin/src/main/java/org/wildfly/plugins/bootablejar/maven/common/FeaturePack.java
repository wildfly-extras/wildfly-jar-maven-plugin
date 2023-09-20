/*
 * Copyright 2016-2020 Red Hat, Inc. and/or its affiliates
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

import org.jboss.galleon.api.GalleonFeaturePack;

public class FeaturePack extends GalleonFeaturePack {

    private String includedDefaultConfig;
    public FeaturePack() {
        // Default to false with WildFly Bootable JAR.
        setInheritPackages(false);
    }
    public String getIncludedDefaultConfig() {
        return includedDefaultConfig;
    }

    public void setIncludedDefaultConfig(String includedDefaultConfig) {
        this.includedDefaultConfig = includedDefaultConfig;
    }

    public String getGAC() {
        StringBuilder builder = new StringBuilder();
        builder.append(getGroupId()).append(":").append(getArtifactId());
        String type = getExtension() == null ? getType() : getExtension();
        if (getClassifier() != null) {
            builder.append(":").append(getClassifier());
        }
        return builder.toString();
    }
}
