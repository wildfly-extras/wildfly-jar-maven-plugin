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
import org.wildfly.plugins.bootablejar.maven.common.OverriddenArtifact;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author jdenise
 */
public class UpgradeArtifactFPLTestCase extends AbstractBootableJarMojoTestCase {

    public UpgradeArtifactFPLTestCase() {
        super("upgrade-artifact-fpl-pom.xml", true, null);
    }

    @Test
    public void testUpgrade() throws Exception {
        BuildBootableJarMojo mojo = lookupMojo("package");
        MavenProjectArtifactVersions artifacts = MavenProjectArtifactVersions.getInstance(mojo.project);
        // We have an older release of wildfly-ee-galleon-pack in the pom.xml
        Assert.assertEquals(1, mojo.overriddenServerArtifacts.size());
        String wildflyeeVersion = null;
        for (OverriddenArtifact oa : mojo.overriddenServerArtifacts) {
            Artifact a = artifacts.getFeaturePackArtifact(oa.getGroupId(), oa.getArtifactId(), oa.getClassifier());
                Assert.assertNotNull(oa.getGroupId() + ":" + oa.getArtifactId(), a);
            wildflyeeVersion = a.getVersion();

            Assert.assertNotNull(a);
            Assert.assertNotNull(a.getVersion());
            Assert.assertEquals(oa.getGroupId(), a.getGroupId());
            Assert.assertEquals(oa.getArtifactId(), a.getArtifactId());
        }
        mojo.recordState = true;
        mojo.execute();
        final Path dir = getTestDir();
        String[] layers = {"jaxrs-server"};
        Path unzippedJar = checkAndGetWildFlyHome(dir, true, true, layers, null);
        try {
            Path modulesDir = unzippedJar.resolve("modules").resolve("system").resolve("layers").resolve("base");
            Path ee = modulesDir.resolve("org").resolve("jboss").resolve("as").resolve("ee").resolve("main").resolve("wildfly-ee-" + wildflyeeVersion + ".jar");
            Assert.assertTrue(ee.toString(), Files.exists(ee));
        } finally {
            BuildBootableJarMojo.deleteDir(unzippedJar);
        }
        checkJar(dir, true, true, layers, null);
        checkDeployment(dir, true);
    }
}
