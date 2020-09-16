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

import org.junit.Test;

/**
 * @author jdenise
 */
public class InvalidSwappingLayerTestCase extends AbstractBootableJarMojoTestCase {
    public InvalidSwappingLayerTestCase() {
        super("invalid8-pom.xml", true, null);
    }

    @Test
    public void test()
            throws Exception {
        BuildBootableJarMojo mojo = lookupMojo("package");
        assertNotNull(mojo);
        boolean failed = false;
        try {
            mojo.execute();
            failed = true;
        } catch(Exception ex) {
            // OK expected
            System.err.println("EXPECTED exception");
            ex.printStackTrace();
        }
        if (failed) {
           throw new Exception("Should have failed");
        }
    }

    @Override
    public void shutdownServer() throws Exception {
        // No server to shutdown.
        // This fixes a test problem on JDK 11, attempt to provision a server has
        // the side effect to set an SSL Security provider that
        // will fail to initialize when shutting down the server, no jboss.home being created.
        // The test execution context doesn't allow us to fork the embedded server.
    }
}
