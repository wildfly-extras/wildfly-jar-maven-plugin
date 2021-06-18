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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/**
 * @author jdenise
 */
public class ServerModeDevServerTestCase extends AbstractBootableJarMojoTestCase {

    public ServerModeDevServerTestCase() {
        super("test-servermode3-pom.xml", true, null);
    }

    @Test
    public void testDevServer() throws Exception {
        DevBootableJarMojo mojo = lookupMojo("dev");
        assertNotNull(mojo);
        assertEquals(1, mojo.excludedLayers.size());
        assertEquals("deployment-scanner", mojo.excludedLayers.get(0));
        mojo.execute();
        final Path dir = getTestDir();
        checkServer(dir, SERVER_DEFAULT_DIR_NAME, 0, false, null, null, "target" + File.separator + "deployments");
        Path config = dir.resolve("target").
                resolve(SERVER_DEFAULT_DIR_NAME).resolve("standalone").resolve("configuration").resolve("standalone.xml");
        assertTrue(Files.exists(config));
        String content = new String(Files.readAllBytes(config), StandardCharsets.UTF_8);
        assertTrue(content.contains(DevBootableJarMojo.DEPLOYMENT_SCANNER_NAME));
        assertFalse(content, content.contains("<deployment-scanner name=\"default\""));
        checkManagementItf(false, dir, false);
    }
}
