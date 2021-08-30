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
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.MojoExecutionException;
import org.wildfly.plugins.bootablejar.maven.common.OverriddenArtifact;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

/**
 * @author jdenise
 */
public class UpgradeArtifactTestCase extends AbstractBootableJarMojoTestCase {

    public UpgradeArtifactTestCase() {
        super("upgrade-artifact-pom.xml", true, null);
    }

    @Test
    public void testUpgrade() throws Exception {
        Assume.assumeFalse("Not stupported on XP", isXP());
        BuildBootableJarMojo mojo = lookupMojo("package");
        MavenProjectArtifactVersions artifacts = MavenProjectArtifactVersions.getInstance(mojo.project);
        // We have an older release of undertow-core and wildfly-ee-galleon-pack in the pom.xml
        Assert.assertEquals(3, mojo.overriddenServerArtifacts.size());
        String undertowVersion = null;
        String wildflyeeVersion = null;
        String restEasySpringVersion = null;
        boolean seenUndertow = false;
        boolean seenRestEasy = false;
        boolean seenFeaturePack = false;
        for (OverriddenArtifact oa : mojo.overriddenServerArtifacts) {
            Artifact a = null;
            if ("io.undertow".equals(oa.getGroupId())) {
                a = artifacts.getArtifact(oa);
                Assert.assertNotNull(oa.getGroupId() + ":" + oa.getArtifactId(), a);
                seenUndertow = true;
                undertowVersion = a.getVersion();
            } else if ("org.jboss.resteasy".equals(oa.getGroupId())) {
                a = artifacts.getArtifact(oa);
                Assert.assertNotNull(oa.getGroupId() + ":" + oa.getArtifactId(), a);
                seenRestEasy = true;
                restEasySpringVersion = a.getVersion();
            } else {
                a = artifacts.getFeaturePackArtifact(oa.getGroupId(), oa.getArtifactId(), oa.getClassifier());
                Assert.assertNotNull(oa.getGroupId() + ":" + oa.getArtifactId(), a);
                seenFeaturePack = true;
                wildflyeeVersion = a.getVersion();
            }
            Assert.assertNotNull(a);
            Assert.assertNotNull(a.getVersion());
            Assert.assertEquals(oa.getGroupId(), a.getGroupId());
            Assert.assertEquals(oa.getArtifactId(), a.getArtifactId());
        }
        Assert.assertTrue(seenUndertow && seenFeaturePack && seenRestEasy);
        mojo.recordState = true;
        mojo.execute();
        final Path dir = getTestDir();
        String[] layers = {"jaxrs-server"};
        Path unzippedJar = checkAndGetWildFlyHome(dir, true, true, layers, null, mojo.recordState);
        try {
            Path modulesDir = unzippedJar.resolve("modules").resolve("system").resolve("layers").resolve("base");
            Path undertow = modulesDir.resolve("io").resolve("undertow").resolve("core").resolve("main").resolve("undertow-core-" + undertowVersion + ".jar");
            Assert.assertTrue(undertow.toString(), Files.exists(undertow));
            Path ee = modulesDir.resolve("org").resolve("jboss").resolve("as").resolve("ee").resolve("main").resolve("wildfly-ee-" + wildflyeeVersion + ".jar");
            Assert.assertTrue(ee.toString(), Files.exists(ee));
            Path resteasy = modulesDir.resolve("org").resolve("jboss").resolve("resteasy").resolve("resteasy-spring").resolve("main").
                    resolve("bundled").resolve("resteasy-spring-jar").resolve("resteasy-spring-" + restEasySpringVersion + ".jar");
            Assert.assertTrue(ee.toString(), Files.exists(ee));
        } finally {
            BuildBootableJarMojo.deleteDir(unzippedJar);
        }
        checkJar(dir, true, true, layers, null, mojo.recordState);
        checkDeployment(dir, true);
    }

    @Test
    public void testInvalidUpgrades() throws Exception {
        BuildBootableJarMojo mojo = lookupMojo("package");
        Assert.assertEquals(3, mojo.overriddenServerArtifacts.size());
        List<OverriddenArtifact> orig = new ArrayList<>();
        orig.addAll(mojo.overriddenServerArtifacts);
        mojo.overriddenServerArtifacts.addAll(mojo.overriddenServerArtifacts);
        try {
            mojo.execute();
            throw new Exception("Should have failed");
        } catch (MojoExecutionException ex) {
            // XXX Expected
        }
        mojo.overriddenServerArtifacts = orig;
        String grpId = orig.get(0).getGroupId();
        try {
            orig.get(0).getGroupId();
            orig.get(0).setGroupId(null);
            mojo.execute();
            throw new Exception("Should have failed");
        } catch (MojoExecutionException ex) {
            // XXX Expected
        }
        orig.get(0).setGroupId(grpId);
        try {
            orig.get(1).setArtifactId(null);
            mojo.execute();
            throw new Exception("Should have failed");
        } catch (MojoExecutionException ex) {
            // XXX Expected
        }

    }
}
