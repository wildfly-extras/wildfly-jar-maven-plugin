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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.jboss.as.controller.client.ModelControllerClient;

import org.wildfly.plugin.core.ServerHelper;
import static org.wildfly.plugins.bootablejar.maven.goals.DevWatchBootableJarMojo.TEST_PROPERTY_EXIT;

/**
 * @author jdenise
 */
public abstract class AbstractDevWatchTestCase extends AbstractBootableJarMojoTestCase {

    static final String RESOURCES_MARKER = "<!-- ###RESOURCES### !-->";
    static final String UPDATED_LAYERS_MARKER = "<!-- ###LAYERS### !-->";

    static final String LOG_SERVER_RESTART = "[WATCH] server re-started";
    static final String LOG_RESET_WATCHER = "[WATCH] Reset the watcher.";
    static final String LOG_REBUILD_JAR = "[WATCH] re-building bootable JAR";

    private Exception processException;
    private Process process;
    private Integer retCode;
    private Path pomFile;
    private Thread goalThread;
    private Path logFile;
    private Path provisioningXml;
    private Path exitFile;
    protected AbstractDevWatchTestCase(String projectName, String testName) {
        super(projectName, testName + "-watch-test", false, null);
    }

    @Override
    public void before() throws Exception {
        super.before();
        pomFile = getTestDir().resolve("pom.xml");
        patchPomFile(pomFile.toFile());
        provisioningXml = getTestDir().resolve("target").
                resolve("bootable-jar-build-artifacts").resolve("jar-content").resolve("provisioning.xml");
        exitFile = getTestDir().resolve("please-exit-test" + System.currentTimeMillis());
    }

    static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("windows");
    }

    void startGoal() throws Exception {
        // Wait, the project filesystem layout has just been created
        Thread.sleep(2000);
        Runnable r = new Runnable() {
            @Override
            public void run() {
                try {
                    // We can't call into the plugin, when reloading itself (after a pom.xml change
                    // the clasloading context switch from AppClassloader to Maven RealmClass and all is reloaded without delegation
                    // breaking fully the loading env.

                    // CI has a different executable path.
                    Boolean isCI = Boolean.getBoolean("org.wildfly.bootable.jar.ci.execution");
                    String prop = "-D" + TEST_PROPERTY_EXIT + "=" + exitFile.getFileName().toString();
                    List<String> cmd = new ArrayList<>();
                    if (isWindows()) {
                        if (isCI) {
                            cmd.add("pwsh.EXE");
                            cmd.add("-command ");
                            cmd.add("mvn");
                            prop = "'" + prop + "'";
                        } else {
                            cmd.add("mvn.cmd");
                        }
                    } else {
                        cmd.add("mvn");
                    }

                    String[] mvnCmd = {"-f", pomFile.toAbsolutePath().toString(), "wildfly-jar:dev-watch", "-e", prop};
                    cmd.addAll(Arrays.asList(mvnCmd));
                    logFile = getTestDir().resolve("target").resolve("dev-watch-test-output.txt");
                    if (Files.exists(logFile)) {
                        Files.delete(logFile);
                    }
                    final Path parent = logFile.getParent();
                    if (parent != null && Files.notExists(parent)) {
                        Files.createDirectories(parent);
                    }
                    process = new ProcessBuilder(cmd).redirectErrorStream(true)
                            .redirectOutput(logFile.toFile()).start();
                    int r = process.waitFor();
                    if (r != 0) {
                        retCode = r;
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    processException = ex;
                }
            }
        };
        goalThread = new Thread(r);
        goalThread.start();
        Exception waitException = null;
        try (ModelControllerClient client = ModelControllerClient.Factory.create(TestEnvironment.getHost(), TestEnvironment.getManagementPort())) {
            ServerHelper.waitForStandalone(client, TestEnvironment.getTimeout() * 5);
            // Give some time for first deployment.
            Thread.sleep(2000);
        } catch (Exception ex) {
            waitException = ex;
        } finally {
            if (waitException != null) {
                if (processException != null) {
                    waitException.addSuppressed(processException);
                }
                throw waitException;
            }
            if (processException != null) {
                throw processException;
            }
        }
    }

    @Override
    public void shutdownServer() throws Exception {
        super.shutdownServerAsync();

        if (process != null) {
            if (retCode != null) {
                Exception ex = new Exception("dev-watch goal process not running although it should. Return code " + retCode);
                if (processException != null) {
                    ex.addSuppressed(processException);
                }
                throw ex;
            }

            Files.createFile(exitFile);
            System.out.println("Created exit file, process should exit.");
            process.waitFor(TestEnvironment.getTimeout(), TimeUnit.SECONDS);
            if (process.isAlive()) {
                process.destroyForcibly();
                throw new Exception("Server didn't exit, something went wrong.");
            } else {
                if (processException != null) {
                    throw processException;
                }
            }
        }
    }

    boolean logFileContains(String msg) throws IOException {
        if (logFile == null || !Files.exists(logFile)) {
            return false;
        }
        String logContent = new String(Files.readAllBytes(logFile), "UTF-8");
        return logContent.contains(msg);
    }

    void waitForLogMessage(String message, int timeout) throws Exception {
        if (logFile == null || !Files.exists(logFile)) {
            throw new Exception("No log file");
        }
        boolean foundMessage = false;
        while (timeout > 0) {
            String content = new String(Files.readAllBytes(logFile), "UTF-8");
            if (content.contains(message)) {
                // OK expected.
                foundMessage = true;
                break;
            }
            Thread.sleep(1000);
            timeout -= 1;
        }
        if (!foundMessage) {
            throw new Exception("Message " + message + " not in log after " + timeout + " seconds");
        }
    }

    boolean layerExists(String layer) throws IOException {
        if (provisioningXml == null || !Files.exists(provisioningXml)) {
            return false;
        }
        return new String(Files.readAllBytes(provisioningXml), "UTF-8").contains(layer);
    }

    void waitForLayer(String layer, int timeout) throws Exception {
        boolean foundLayer = false;
        while (timeout > 0) {
            try {
                if (Files.exists(provisioningXml)) {
                    String content = new String(Files.readAllBytes(provisioningXml), "UTF-8");
                    if (content.contains("jmx")) {
                        // OK expected.
                        foundLayer = true;
                        break;
                    }
                }
            } catch (Exception ex) {
                // Ignore exception that could occur trying to read a file being deleted during rebuild
            }
            Thread.sleep(1000);
            timeout -= 1;
        }
        if (!foundLayer) {
            throw new Exception(layer + " layer not found in the set of provisioned layers");
        }
    }
}
