/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * @author jdenise
 */
public class ServerModeMultipleDeploymentsTestCase extends AbstractBootableJarMojoTestCase {

    private Path pomFile;

    public ServerModeMultipleDeploymentsTestCase() {
        super("multiple-deployments", "mutiple-deployments-test", false, null);
    }

    @Override
    public void before() throws Exception {
        super.before();
        pomFile = getTestDir().resolve("server").resolve("pom.xml");
        patchPomFile(pomFile.toFile());
    }

    @Test
    public void testMultipleDeployments() throws Exception {
        installArtifact(getTestDir().resolve("war1").resolve("pom.xml"));
        installArtifact(getTestDir().resolve("war2").resolve("pom.xml"));
        Path serverDir = getTestDir().resolve("server");
        Path serverPomFile = serverDir.resolve("pom.xml");
        BuildBootableJarMojo mojo = lookupMojo(serverPomFile, "package");
        mojo.execute();
        checkServer(serverDir, SERVER_DEFAULT_DIR_NAME, 2, false, null, null);
        checkURL(false, serverDir, null, createUrl(TestEnvironment.getHttpPort(), "war1"), true);
        checkURL(false, serverDir, null, createUrl(TestEnvironment.getHttpPort(), "war2"), true);
    }

    private void installArtifact(Path file) throws IOException, InterruptedException {
        List<String> cmd = mvnCommand(file, "install", Collections.emptyList());
        Path logFile = getTestDir().resolve("target").resolve("war-install-test-output.txt");
        if (Files.exists(logFile)) {
            Files.delete(logFile);
        }
        final Path parent = logFile.getParent();
        if (parent != null && Files.notExists(parent)) {
            Files.createDirectories(parent);
        }
        Process process = new ProcessBuilder(cmd).redirectErrorStream(true)
                .redirectOutput(logFile.toFile()).start();
        int r = process.waitFor();
        if (r != 0) {
            throw new RuntimeException("Fail to install war dependency " + file);
        }
    }
}
