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
public class CustomFileNameTestCase extends AbstractBootableJarMojoTestCase {

    public CustomFileNameTestCase() {
        super("test11-pom.xml", true, null);
    }

    @Test
    public void testCustomFileName() throws Exception {
        BuildBootableJarMojo mojo = lookupMojo("package");
        assertNotNull(mojo);
        assertNotNull(mojo.outputFileName);
        mojo.execute();
        final Path dir = getTestDir();
        Path jar = dir.resolve("target").resolve("foo.jar");
        assertTrue(Files.exists(jar));
        checkDeployment(dir, "foo.jar", true);
    }
}
