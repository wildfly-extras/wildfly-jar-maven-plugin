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

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/**
 * @author jdenise
 */
public class DevWatchInvalidProvisioningTestCase extends AbstractDevWatchTestCase {

    public DevWatchInvalidProvisioningTestCase() {
        super("jaxrs", "DevWatchInvalidProvisioning");
    }

    @Test
    public void testDevWInvalidProvisioning() throws Exception {
        startGoal();

        String url = createUrl(TestEnvironment.getHttpPort(), "rest/hello");
        String radical = "Hello from ";
        String msg = "WildFly bootable jar!";
        String expectedContent = radical + msg;
        pollBodyContent(url, expectedContent);

        // Add invalid layer
        String updatedLayers = "<layer>foo</layer>";
        Path pomFile = getTestDir().resolve("pom.xml");
        String pomContent = new String(Files.readAllBytes(pomFile), "UTF-8");
        pomContent = pomContent.replace(UPDATED_LAYERS_MARKER, updatedLayers);
        Files.write(pomFile, pomContent.getBytes());
        waitForLogMessage("Failed to locate layer [model=standalone name=foo]", TestEnvironment.getTimeout());

        // Add a valid layer
        String layer = "<layer>jmx</layer>";
        pomContent = pomContent.replace(updatedLayers, layer);
        Files.write(pomFile, pomContent.getBytes());

        waitForLogMessage(LOG_REBUILD_JAR, TestEnvironment.getTimeout());
        waitForLayer(layer, TestEnvironment.getTimeout());
        waitForLogMessage(LOG_SERVER_RESTART, TestEnvironment.getTimeout());
        // Server has been re-started, retrieve the endpoint returned string
        assertTrue(pollBodyContent(url, expectedContent));
    }
}
