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

import org.apache.maven.plugin.MojoExecutionException;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author jdenise
 */
public class UpgradeArtifactUnknownArtifactTestCase extends AbstractBootableJarMojoTestCase {

    public UpgradeArtifactUnknownArtifactTestCase() {
        super("invalid-upgrade-unknown-artifact-pom.xml", true, null);
    }

    @Test
    public void testUpgrade() throws Exception {
        BuildBootableJarMojo mojo = lookupMojo("package");
        try {
            mojo.execute();
            throw new Exception("Should have failed");
        } catch (MojoExecutionException ex) {
            Assert.assertTrue(ex.toString().contains("Overridden artifact jakarta.platform:jakarta.jakartaee-api not know in provisioned feature-packs"));
            // XXX OK, expected
        }
    }
}
