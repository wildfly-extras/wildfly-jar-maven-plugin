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

import java.nio.file.Path;

import org.junit.Test;
import static org.wildfly.plugins.bootablejar.patching.PatchingTestUtil.buildMiscPatch;
import static org.wildfly.plugins.bootablejar.patching.PatchingTestUtil.randomString;

/**
 * @author jdenise
 */
public class PatchUnexistingFailMiscTestCase extends AbstractBootableJarMojoTestCase {
    public PatchUnexistingFailMiscTestCase() {
        super("test15-pom.xml", true, null);
    }

    @Test
    public void testMiscPatch()
            throws Exception {
        String patchid = randomString();
        Path patchContentDir = createTestDirectory("patch-test-content", patchid);
        final String testContent = "java -version";
        buildMiscPatch(patchContentDir, false, getTestDir(), patchid, testContent, "bin", "jboss-cli.sh");
        BuildBootableJarMojo mojo = lookupMojo("package");
        assertNotNull(mojo);
        boolean failed = false;
        try {
            mojo.execute();
            failed = true;
        } catch(Exception ex) {
            // OK expected
        }
        if (failed) {
           throw new Exception("Should have failed");
        }
    }
}
