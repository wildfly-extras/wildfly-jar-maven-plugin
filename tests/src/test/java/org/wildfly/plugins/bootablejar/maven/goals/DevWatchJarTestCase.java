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
public class DevWatchJarTestCase extends AbstractDevWatchTestCase {

    private final String artifactName;

    public DevWatchJarTestCase() {
        super("jar", "DevWatchJar");
        artifactName = "test2";
    }

    protected DevWatchJarTestCase(String projectName, String testName, String artifactName) {
        super(projectName, testName);
        this.artifactName = artifactName;
    }

    @Test
    public void testDevWatchJar() throws Exception {
        startGoal();

        // Does not contain extra layers added during the test
        assertFalse(layerExists("jmx"));
        assertTrue(layerExists("ejb-lite"));

        Path targetDir = getTestDir().resolve("target").resolve("deployments").resolve(artifactName + ".jar");
        assertTrue(Files.exists(targetDir));
        assertTrue(Files.isDirectory(targetDir));

        String currentName1 = "FOO";
        String currentName2 = "BABAR";
        String radical = "java:app/" + artifactName + "/";
        waitForLogMessage(radical + currentName1, TestEnvironment.getTimeout());
        waitForLogMessage(radical + currentName2, TestEnvironment.getTimeout());

        // Update Java file and check for change.
        Path javaFile = getTestDir().resolve("src").resolve("main").resolve("java").
                resolve("org").resolve("wildfly").resolve("plugins").resolve("demo").resolve("ejb").resolve("GreeterEJB.java");
        String str = new String(Files.readAllBytes(javaFile), "UTF-8");
        String newName = "FOO" + System.currentTimeMillis();
        str = str.replace(currentName1, newName);
        currentName1 = newName;
        Files.write(javaFile, str.getBytes());

        waitForLogMessage(radical + currentName1, TestEnvironment.getTimeout());
        // Update resources file and check.
        Path ejbJarXml = getTestDir().resolve("src").resolve("main").resolve("resources").
                resolve("META-INF").resolve("ejb-jar.xml");
        String xmlContent = new String(Files.readAllBytes(ejbJarXml), "UTF-8");
        String newName2 = "XMAN" + System.currentTimeMillis();
        xmlContent = xmlContent.replace(currentName2, newName2);
        currentName2 = newName2;
        Files.write(ejbJarXml, xmlContent.getBytes());

        waitForLogMessage(radical + currentName2, TestEnvironment.getTimeout());

        // Add extra layers!
        Path pomFile = getTestDir().resolve("pom.xml");
        String updatedLayers = "<layer>jmx</layer>";
        String pomContent = new String(Files.readAllBytes(pomFile), "UTF-8");
        pomContent = pomContent.replace(UPDATED_LAYERS_MARKER, updatedLayers);
        Files.write(pomFile, pomContent.getBytes());
        waitForLogMessage(LOG_REBUILD_JAR, TestEnvironment.getTimeout());

        // In // make change to the java src code, will be recompiled when restarting.
        // These shouldn't be seen.
        str = new String(Files.readAllBytes(javaFile), "UTF-8");
        newName = "ADDED-DURING-REBUILD-FOO" + System.currentTimeMillis();
        str = str.replace(currentName1, newName);
        currentName1 = newName;
        Files.write(javaFile, str.getBytes());

        waitForLayer(str, TestEnvironment.getTimeout());
        waitForLogMessage(LOG_SERVER_RESTART, TestEnvironment.getTimeout());
        waitForLogMessage(radical + currentName1, TestEnvironment.getTimeout());
    }
}
