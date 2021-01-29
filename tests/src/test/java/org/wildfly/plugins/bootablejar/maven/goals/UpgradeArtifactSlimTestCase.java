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

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.artifact.Artifact;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author jdenise
 */
public class UpgradeArtifactSlimTestCase extends AbstractBootableJarMojoTestCase {

    public UpgradeArtifactSlimTestCase() {
        super("upgrade-artifact-slim-pom.xml", true, null);
    }

    @Test
    public void testUpgrade() throws Exception {
        BuildBootableJarMojo mojo = lookupMojo("package");
        MavenProjectArtifactVersions artifacts = MavenProjectArtifactVersions.getInstance(mojo.project);
        // We have an older release of undertow-core in the pom.xml
        Assert.assertEquals(1, mojo.overriddenServerArtifacts.size());
        Artifact a = artifacts.getArtifact(mojo.overriddenServerArtifacts.get(0));
        String undertowVersion =  a.getVersion();
        Assert.assertNotNull(undertowVersion);
        mojo.recordState = true;
        mojo.execute();
        final Path dir = getTestDir();
        String[] layers = {"jaxrs-server"};
        Path unzippedJar = checkAndGetWildFlyHome(dir, true, true, layers, null);
        try {
            Path modulesDir = unzippedJar.resolve("modules").resolve("system").resolve("layers").resolve("base");
            Path module = modulesDir.resolve("io").resolve("undertow").resolve("core").resolve("main").resolve("module.xml");
            Assert.assertTrue(module.toString(), Files.exists(module));
            String moduleContent = new String(Files.readAllBytes(module), "UTF-8");
            Assert.assertTrue(moduleContent.contains("artifact name=\"io.undertow:undertow-core:"+undertowVersion+"\""));
            Path undertow = modulesDir.resolve("io").resolve("undertow").resolve("core").resolve("main").resolve("undertow-core-" + undertowVersion + ".jar");
            Assert.assertFalse(undertow.toString(), Files.exists(undertow));
        } finally {
            BuildBootableJarMojo.deleteDir(unzippedJar);
        }
        checkJar(dir, true, true, layers, null);
        checkDeployment(dir, true);
    }
}
