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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.jboss.as.patching.HashUtils;
import org.jboss.as.patching.metadata.ContentModification;

import org.junit.Test;
import org.wildfly.plugins.bootablejar.patching.ContentModificationUtils;
import org.wildfly.plugins.bootablejar.patching.Module;
import org.wildfly.plugins.bootablejar.patching.PatchingTestUtil;
import static org.wildfly.plugins.bootablejar.patching.PatchingTestUtil.AS_DISTRIBUTION;
import static org.wildfly.plugins.bootablejar.patching.PatchingTestUtil.FILE_SEPARATOR;
import static org.wildfly.plugins.bootablejar.patching.PatchingTestUtil.MODULES_PATH;
import static org.wildfly.plugins.bootablejar.patching.PatchingTestUtil.RELATIVE_MODULES_PATH;
import static org.wildfly.plugins.bootablejar.patching.PatchingTestUtil.RELATIVE_PATCHES_PATH;
import static org.wildfly.plugins.bootablejar.patching.PatchingTestUtil.buildCPPatch;
import static org.wildfly.plugins.bootablejar.patching.PatchingTestUtil.randomString;
import org.wildfly.plugins.bootablejar.patching.ResourceItem;

/**
 * @author jdenise
 */
public class PatchCPTestCase extends AbstractBootableJarMojoTestCase {

    public PatchCPTestCase() {
        super("test15-pom.xml", true, null);
    }

    protected PatchCPTestCase(final String pomFileName, final boolean copyWar, final String provisioning, final String... cli) {
        super(pomFileName, copyWar, provisioning, cli);
    }

    @Test
    public void testCPPatch()
            throws Exception {
        String patchID = randomString();
        String layerPatchID = "layer" + patchID;
        Path patchContentDir = createTestDirectory("patch-test-content", patchID);

        Module newModuleA = new Module.Builder("org.wildfly.test.A")
                .dependency("org.wildfly.test.B")
                .build();
        ContentModification moduleAddedA = ContentModificationUtils.addModule(patchContentDir.toFile(), layerPatchID, newModuleA);
        Module newModuleB = new Module.Builder("org.wildfly.test.B")
                .dependency("org.wildfly.test.C")
                .dependency("org.wildfly.test.D")
                .build();
        ContentModification moduleAddedB = ContentModificationUtils.addModule(patchContentDir.toFile(), layerPatchID, newModuleB);
        Module newModuleC = new Module.Builder("org.wildfly.test.C")
                .build();
        ContentModification moduleAddedC = ContentModificationUtils.addModule(patchContentDir.toFile(), layerPatchID, newModuleC);
        Module newModuleD = new Module.Builder("org.wildfly.test.D")
                .build();
        ContentModification moduleAddedD = ContentModificationUtils.addModule(patchContentDir.toFile(), layerPatchID, newModuleD);
        Module newModuleE = new Module.Builder("org.wildfly.test.E")
                .build();
        ContentModification moduleAddedE = ContentModificationUtils.addModule(patchContentDir.toFile(), layerPatchID, newModuleE);

        final String serverModuleName = "org.jboss.as.server";
        Path moduleDir = Paths.get(AS_DISTRIBUTION);
        moduleDir = moduleDir.resolve("modules").resolve("system").resolve("layers").
                resolve("base").resolve("org").resolve("jboss").resolve("as").resolve("server").resolve("main");
        assertTrue(Files.exists(moduleDir));
        Path moduleFile = moduleDir.resolve("module.xml");
        Module updatedModule = new Module.Builder(serverModuleName)
                .miscFile(new ResourceItem("res1", "new resource in the module".getBytes(StandardCharsets.UTF_8)))
                .originalModuleXml(moduleFile)
                .property("foo", "bar")
                .dependency("org.wildfly.test.A")
                .build();

        // Add cli module that doesn't exist
        final String cliModuleName = "org.jboss.as.cli";
        Path cliModuleDir = Paths.get(AS_DISTRIBUTION);
        cliModuleDir = cliModuleDir.resolve("modules").resolve("system").resolve("layers").
                resolve("base").resolve("org").resolve("jboss").resolve("as").resolve("cli").resolve("main");
        assertTrue(Files.exists(cliModuleDir));
        Path cliModuleFile = cliModuleDir.resolve("module.xml");
        Module cliUpdatedModule = new Module.Builder(cliModuleName)
                .miscFile(new ResourceItem("res1", "new resource in the module".getBytes(StandardCharsets.UTF_8)))
                .originalModuleXml(cliModuleFile)
                .property("foo", "bar")
                .build();
        // create the patch with the updated module
        ContentModification cliModuleModified = ContentModificationUtils.modifyModule(patchContentDir.toFile(),
                layerPatchID, HashUtils.hashFile(cliModuleDir.toFile()), cliUpdatedModule);

        // Also see if we can update jboss-modules
        Path installation = Paths.get(AS_DISTRIBUTION);
        Path patchDir = patchContentDir.resolve(patchID);
        final ContentModification jbossModulesModification = PatchingTestUtil.updateModulesJar(installation.toFile(), patchDir.toFile());

        // create the patch with the updated module
        ContentModification moduleModified = ContentModificationUtils.modifyModule(patchContentDir.toFile(),
                layerPatchID, HashUtils.hashFile(moduleDir.toFile()), updatedModule);

        // Create the version module
        final String cpAsVersion = "EAP with cp patch";
        final String versionModuleName = "org.jboss.as.version";
        final String slot = "main";
        final String originalVersionModulePath = MODULES_PATH + FILE_SEPARATOR + versionModuleName.replace(".", FILE_SEPARATOR) + FILE_SEPARATOR + slot;
        final Module modifiedModule = PatchingTestUtil.createVersionModule(cpAsVersion);
        ContentModification versionModuleModified = ContentModificationUtils.modifyModule(patchContentDir.toFile(), layerPatchID, HashUtils.hashFile(new File(originalVersionModulePath)), modifiedModule);

        final Path dir = getTestDir();
        buildCPPatch(patchContentDir, true, dir, patchID, cpAsVersion, moduleModified,
                layerPatchID, versionModuleModified, cliModuleModified, jbossModulesModification,
                moduleAddedA, moduleAddedB, moduleAddedC, moduleAddedD, moduleAddedE);
        BuildBootableJarMojo mojo = lookupMojo("package");
        assertNotNull(mojo);
        mojo.execute();
        Path home = checkAndGetWildFlyHome(dir, true, true, null, null);
        try {

            checkJar(dir, true, true, null, null);
            checkDeployment(dir, true);
        } finally {
            BuildBootableJarMojo.deleteDir(home);
        }
    }

    protected void checkFiles(Path home, String layerPatchID, String serverModuleName, String cliModuleName,
            String moduleA, String moduleB, String moduleC, String moduleD, String moduleE) {
        // original module
        final String modulePath = home.toString() + FILE_SEPARATOR + RELATIVE_MODULES_PATH
                + FILE_SEPARATOR + serverModuleName.replace(".", FILE_SEPARATOR) + FILE_SEPARATOR + "main";
        assertTrue(Files.exists(Paths.get(modulePath)));
        // The patched module
        final String patchedModulePath = home.toString() + FILE_SEPARATOR + RELATIVE_PATCHES_PATH
                + FILE_SEPARATOR + layerPatchID + FILE_SEPARATOR + serverModuleName.replace(".", FILE_SEPARATOR) + FILE_SEPARATOR + "main";
        assertTrue(Files.exists(Paths.get(patchedModulePath)));
        // The module exists because override-all
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

        final String moduleEPath = home.toString() + FILE_SEPARATOR + RELATIVE_PATCHES_PATH
                + FILE_SEPARATOR + layerPatchID + FILE_SEPARATOR + moduleE.replace(".", FILE_SEPARATOR) + FILE_SEPARATOR + "main";
        assertTrue(Files.exists(Paths.get(moduleEPath)));

        Path patches = home.resolve(".installation").resolve("patches");
        assertTrue(Files.exists(patches));
    }
}
