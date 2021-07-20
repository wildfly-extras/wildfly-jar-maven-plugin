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

import java.nio.file.Path;

import org.junit.Test;

/**
 * @author jdenise
 */
public class IncludedDefaultConfigurationTestCase extends AbstractBootableJarMojoTestCase {

    public IncludedDefaultConfigurationTestCase() {
        super("test12-pom.xml", true, null);
    }

    @Test
    public void testLayersConfiguration() throws Exception {
        BuildBootableJarMojo mojo = lookupMojo("package");
        assertNotNull(mojo);
        assertFalse(mojo.excludedLayers.isEmpty());
        assertEquals(1, mojo.excludedLayers.size());
        assertEquals("h2-default-datasource", mojo.excludedLayers.get(0));
        mojo.recordState = true;
        mojo.execute();
        String[] excludedLayers = {"h2-default-datasource"};
        final Path dir = getTestDir();
        checkJar(dir, true, true, null, excludedLayers, mojo.recordState);
        checkDeployment(dir, true);
    }
}
