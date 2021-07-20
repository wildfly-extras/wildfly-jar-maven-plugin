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
public class LayersOverrideProvisioningConfigurationTestCase extends AbstractBootableJarMojoTestCase {

    public LayersOverrideProvisioningConfigurationTestCase() {
        super("test2-pom.xml", true, "provisioning1.xml", "add-prop.cli", "add-prop2.cli");
    }

    @Test
    public void testLayersOverrideProvisioningConfiguration() throws Exception {
        BuildBootableJarMojo mojo = lookupMojo("package");
        assertNotNull(mojo);
        assertEquals(1, mojo.cliSessions.size());
        CliSession session = mojo.cliSessions.get(0);
        assertEquals(2, session.getScriptFiles().size());
        assertEquals("add-prop.cli", session.getScriptFiles().get(0));
        assertEquals("add-prop2.cli", session.getScriptFiles().get(1));
        mojo.recordState = true;
        mojo.execute();
        String[] layers = {"jaxrs", "management"};
        final Path dir = getTestDir();
        checkJar(dir, true, true, layers, null, mojo.recordState, "foobootable", "foobootable2");
        checkDeployment(dir, true);
    }
}
