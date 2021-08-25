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

import org.junit.Assert;
import org.junit.Test;

/**
 * @author jdenise
 */
public class UpgradeArtifactDumpTestCase extends AbstractBootableJarMojoTestCase {

    public UpgradeArtifactDumpTestCase() {
        super("upgrade-artifact-dump-pom.xml", true, null);
    }

    @Test
    public void testUpgrade() throws Exception {
        BuildBootableJarMojo mojo = lookupMojo("package");
        mojo.recordState = true;
        mojo.execute();
        final Path dir = getTestDir();
        String[] layers = {"jaxrs-server"};
        Path generatedFile = dir.resolve("target").resolve("bootable-jar-build-artifacts").resolve("bootable-jar-server-original-artifacts.xml");
        Assert.assertTrue(Files.exists(generatedFile));
        String str = new String(Files.readAllBytes(generatedFile), "UTF-8");
        Assert.assertTrue(str.contains("wildfly-ee-galleon-pack"));
        Assert.assertTrue(str.contains("org.jboss.modules"));
        Assert.assertTrue(str.contains("jboss-modules"));
        Assert.assertTrue(str.contains("io.undertow"));
        Assert.assertTrue(str.contains("undertow-core"));
        checkJar(dir, true, true, layers, null);
    }
}
