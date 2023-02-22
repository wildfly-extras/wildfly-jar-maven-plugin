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
package org.wildfly.plugins.bootablejar.maven.preconfigure;

import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.configuration.PlexusConfigurationException;
import org.jboss.galleon.util.ZipUtils;
import org.wildfly.plugins.bootablejar.maven.goals.BuildBootableJarMojo;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Add a configuration capability for enabling/disabling the preconfigure extension scripts
 * in a bootable jar deployment.
 *
 * @author chrisruffalo
 */
public class PreconfigureConfig {

    boolean enabled = false;

    public void copyExtraContent(BuildBootableJarMojo mojo, Path wildflyDir, Path contentDir) throws IOException, PlexusConfigurationException, MojoExecutionException {
        Path extensionJar = mojo.resolveArtifact("org.wildfly.plugins", "wildfly-jar-preconfigure-extension", null, mojo.retrievePluginVersion());
        ZipUtils.unzip(extensionJar, contentDir); // todo: the service file needs to be concatenated
    }

    public boolean isEnabled() {
        return enabled;
    }
}
