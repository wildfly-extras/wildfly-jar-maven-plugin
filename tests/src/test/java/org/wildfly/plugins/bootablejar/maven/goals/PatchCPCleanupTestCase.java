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
import java.nio.file.Paths;
import static org.wildfly.plugins.bootablejar.patching.PatchingTestUtil.FILE_SEPARATOR;
import static org.wildfly.plugins.bootablejar.patching.PatchingTestUtil.RELATIVE_MODULES_PATH;
import static org.wildfly.plugins.bootablejar.patching.PatchingTestUtil.RELATIVE_PATCHES_PATH;

/**
 * @author jdenise
 */
public class PatchCPCleanupTestCase extends PatchCPTestCase {

    public PatchCPCleanupTestCase() {
        super("test16-pom.xml", true, null);
    }

    @Override
    protected void checkFiles(Path home, String layerPatchID, String serverModuleName, String cliModuleName,
            String moduleA, String moduleB, String moduleC, String moduleD, String moduleE) {
        // original module doesn't exists because cleanup
        final String modulePath = home.toString() + FILE_SEPARATOR + RELATIVE_MODULES_PATH
                + FILE_SEPARATOR + serverModuleName.replace(".", FILE_SEPARATOR) + FILE_SEPARATOR + "main";
        assertFalse(Files.exists(Paths.get(modulePath)));
        // The patched module
        final String patchedModulePath = home.toString() + FILE_SEPARATOR + RELATIVE_PATCHES_PATH
                + FILE_SEPARATOR + layerPatchID + FILE_SEPARATOR + serverModuleName.replace(".", FILE_SEPARATOR) + FILE_SEPARATOR + "main";
        assertTrue(Files.exists(Paths.get(patchedModulePath)));
        // The module doesn't exists because cleanup
        final String cliModulePath = home.toString() + FILE_SEPARATOR + RELATIVE_PATCHES_PATH
                + FILE_SEPARATOR + layerPatchID + FILE_SEPARATOR + cliModuleName.replace(".", FILE_SEPARATOR) + FILE_SEPARATOR + "main";
        assertTrue(Files.exists(Paths.get(cliModulePath)));

        final String moduleAPath = home.toString() + FILE_SEPARATOR + RELATIVE_PATCHES_PATH
                + FILE_SEPARATOR + layerPatchID + FILE_SEPARATOR + moduleA.replace(".", FILE_SEPARATOR) + FILE_SEPARATOR + "main";
        assertTrue(Files.exists(Paths.get(moduleAPath)));

        final String moduleBPath = home.toString() + FILE_SEPARATOR + RELATIVE_PATCHES_PATH
                + FILE_SEPARATOR + layerPatchID + FILE_SEPARATOR + moduleB.replace(".", FILE_SEPARATOR) + FILE_SEPARATOR + "main";
        assertTrue(Files.exists(Paths.get(moduleBPath)));

        final String moduleCPath = home.toString() + FILE_SEPARATOR + RELATIVE_PATCHES_PATH
                + FILE_SEPARATOR + layerPatchID + FILE_SEPARATOR + moduleC.replace(".", FILE_SEPARATOR) + FILE_SEPARATOR + "main";
        assertTrue(Files.exists(Paths.get(moduleCPath)));

        final String moduleDPath = home.toString() + FILE_SEPARATOR + RELATIVE_PATCHES_PATH
                + FILE_SEPARATOR + layerPatchID + FILE_SEPARATOR + moduleD.replace(".", FILE_SEPARATOR) + FILE_SEPARATOR + "main";
        assertTrue(Files.exists(Paths.get(moduleDPath)));

        // doesn't exists because cleanup
        final String moduleEPath = home.toString() + FILE_SEPARATOR + RELATIVE_PATCHES_PATH
                + FILE_SEPARATOR + layerPatchID + FILE_SEPARATOR + moduleE.replace(".", FILE_SEPARATOR) + FILE_SEPARATOR + "main";
        assertFalse(Files.exists(Paths.get(moduleEPath)));

        Path patches = home.resolve(".installation").resolve("patches");
        assertFalse(Files.exists(patches));

    }
}
