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
public class DevWatchCliTestCase extends AbstractDevWatchTestCase {

    public DevWatchCliTestCase() {
        super("jaxrs", "DevWatchCli");
    }

    @Test
    public void testCliScript() throws Exception {
        startGoal();

        Path targetDir = getTestDir().resolve("target").resolve("deployments").resolve("ROOT.war");
        assertTrue(targetDir.toString(), Files.exists(targetDir));
        assertTrue(targetDir.toString(), Files.isDirectory(targetDir));
        String url = createUrl(TestEnvironment.getHttpPort(), "rest/hello");
        String radical = "Hello from ";
        String msg = "WildFly bootable jar!";
        String expectedContent = radical + msg;
        pollBodyContent(url, expectedContent);

        Path cliScript = getTestDir().resolve("scripts").resolve("logging.cli");
        Files.write(cliScript, "".getBytes());
        waitForLogMessage(LOG_REBUILD_JAR, TestEnvironment.getTimeout());
        waitForLogMessage(LOG_SERVER_RESTART, TestEnvironment.getTimeout());
        assertTrue(pollBodyContent(url, expectedContent));

    }
}
