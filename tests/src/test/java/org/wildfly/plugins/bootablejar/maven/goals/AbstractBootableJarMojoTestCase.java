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
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpEntity;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.maven.plugin.Mojo;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.dmr.ModelNode;
import org.jboss.galleon.config.ConfigId;
import org.jboss.galleon.config.ConfigModel;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.util.PathsUtils;
import org.jboss.galleon.util.ZipUtils;
import org.jboss.galleon.util.IoUtils;
import org.jboss.galleon.xml.ProvisioningXmlParser;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.wildfly.core.launcher.ProcessHelper;
import org.wildfly.core.launcher.StandaloneCommandBuilder;
import org.wildfly.plugin.core.ServerHelper;
import static org.wildfly.plugins.bootablejar.maven.goals.AbstractDevWatchTestCase.isWindows;

/**
 * @author jdenise
 */
// Still using JUnit3 in AbstractMojoTestCase class.
@RunWith(JUnit4.class)
@SuppressWarnings("SameParameterValue")
public abstract class AbstractBootableJarMojoTestCase extends AbstractConfiguredMojoTestCase {

    static final String WILDFLY_FPL = "test.fpl";
    static final String WILDFLY_VERSION = "test.version.wildfly";
    static final String WILDFLY_EE_VERSION = "test.version.wildfly-ee.upgrade";
    static final String WILDFLY_GROUP_ID = "test.groupid.wildfly";
    static final String PLUGIN_VERSION = "test.plugin.version";
    private static final String TEST_REPLACE = "TEST_REPLACE";
    private static final String TEST_REPLACE_WF_EE_VERSION = "WF_EE_VERSION";
    private static final String TEST_REPLACE_WF_GROUPID = "WF_GROUPID";
    private static final String TEST_REPLACE_WF_VERSION = "WF_VERSION";
    static final String PLUGIN_VERSION_TEST_REPLACE = "PLUGIN_VERSION";
    static final String TEST_FILE = "test-" + AbstractBuildBootableJarMojo.BOOTABLE_SUFFIX + ".jar";
    static final String SERVER_DEFAULT_DIR_NAME ="server";
    private final String projectFile;
    private final boolean copyWar;
    private final String provisioning;
    private final String[] cli;
    private final Path testDir;

    protected AbstractBootableJarMojoTestCase(final String pomFileName, final boolean copyWar, final String provisioning, final String... cli) {
        this(pomFileName, "bootable-jar-test-" + System.currentTimeMillis(), copyWar, provisioning, cli);
    }

    protected AbstractBootableJarMojoTestCase(final String pomFileName, String testName, final boolean copyWar, final String provisioning, final String... cli) {
        this.projectFile = pomFileName;
        this.copyWar = copyWar;
        this.provisioning = provisioning;
        this.cli = cli;
        testDir = createTestDirectory(testName.toLowerCase());
    }

    final boolean isProject() {
        return !projectFile.endsWith(".xml");
    }

    final boolean isXP() {
        return System.getProperties().stringPropertyNames().contains("xp");
    }

    @Before
    public void before() throws Exception {
        // Set the test.name property for the logging.properties. This must be done here as the super.setUp() creates
        // a logger which initializes the log manager
        System.setProperty("test.name", getClass().getCanonicalName());
        super.setUp();
        setupProject();
    }

    @After
    public void shutdownServer() throws Exception {
        try {
            shutdownAsync();
        } finally {
            //Delete the build artifact dir
            Path buildArtifacts = getTestDir().resolve("target").resolve("bootable-jar-build-artifacts/");
            BuildBootableJarMojo.deleteDir(buildArtifacts);
            Path serverDir = getTestDir().resolve("target").resolve("server");
            BuildBootableJarMojo.deleteDir(serverDir);
        }
    }

    public void shutdownServerAsync() throws Exception {
        try {
            shutdownAsync();
        } finally {
            //Delete the build artifact dir
            Path buildArtifacts = getTestDir().resolve("target").resolve("bootable-jar-build-artifacts/");
            BuildBootableJarMojo.deleteDir(buildArtifacts);
            Path serverDir = getTestDir().resolve("target").resolve("server");
            BuildBootableJarMojo.deleteDir(serverDir);
        }
    }

    Path getTestDir() {
        return testDir;
    }

    @SuppressWarnings("unchecked")
    <T extends AbstractBuildBootableJarMojo> T lookupMojo(final String goal) throws Exception {
        return (T) lookupConfiguredMojo(testDir.resolve("pom.xml").toFile(), goal);
    }

    @SuppressWarnings("unchecked")
    <T extends AbstractBuildBootableJarMojo> T lookupMojo(Path pomFile, final String goal) throws Exception {
        return (T) lookupConfiguredMojo(pomFile.toFile(), goal);
    }

    @Override
    protected Mojo lookupConfiguredMojo(File pom, String goal) throws Exception {
        patchPomFile(pom);
        Mojo mojo = super.lookupConfiguredMojo(pom, goal);
        return mojo;
    }

    protected void patchPomFile(File pom) throws IOException {
        StringBuilder content = new StringBuilder();
        for (String s : Files.readAllLines(pom.toPath())) {
            if (s.contains(TEST_REPLACE)) {
                s = s.replace(TEST_REPLACE, System.getProperty(WILDFLY_FPL));
            }
            if (s.contains(PLUGIN_VERSION_TEST_REPLACE)) {
                s = s.replace(PLUGIN_VERSION_TEST_REPLACE, System.getProperty(PLUGIN_VERSION));
            }
            if (s.contains(TEST_REPLACE_WF_VERSION)) {
                s = s.replace(TEST_REPLACE_WF_VERSION, System.getProperty(WILDFLY_VERSION));
            }
            if (s.contains(TEST_REPLACE_WF_EE_VERSION)) {
                s = s.replace(TEST_REPLACE_WF_EE_VERSION, System.getProperty(WILDFLY_EE_VERSION));
            }
            if (s.contains(TEST_REPLACE_WF_GROUPID)) {
                s = s.replace(TEST_REPLACE_WF_GROUPID, System.getProperty(WILDFLY_GROUP_ID));
            }
            content.append(s).append(System.lineSeparator());
        }
        Files.write(pom.toPath(), content.toString().getBytes());
    }

    private void setupProject() throws IOException {
        if (!isProject()) {
            File pom = getTestFile("src/test/resources/poms/" + projectFile);
            File clientPom = getTestFile("src/test/resources/poms/client-pom.xml");
            File war = getTestFile("src/test/resources/test.war");
            assertNotNull(pom);
            assertTrue(pom.exists());
            assertNotNull(war);
            assertTrue(war.exists());

            Path pomFile = testDir.resolve("pom.xml");
            Path clientPomFile = testDir.resolve("client-pom.xml");
            Path target = Files.createDirectory(testDir.resolve("target"));
            if (copyWar) {
                Files.copy(war.toPath(), target.resolve(war.getName()));
            }
            if (provisioning != null) {
                File prov = getTestFile("src/test/resources/provisioning/" + provisioning);
                assertNotNull(prov);
                assertTrue(prov.exists());
                Path galleon = Files.createDirectory(testDir.resolve("galleon"));
                StringBuilder content = new StringBuilder();
                for (String s : Files.readAllLines(prov.toPath())) {
                    if (s.contains(TEST_REPLACE)) {
                        s = s.replace(TEST_REPLACE, System.getProperty(WILDFLY_FPL));
                    }
                    content.append(s);
                }
                Files.write(galleon.resolve("provisioning.xml"), content.toString().getBytes());
            }
            if (cli != null) {
                for (String p : cli) {
                    File cliFile = getTestFile("src/test/resources/cli/" + p);
                    assertNotNull(cliFile);
                    assertTrue(cliFile.exists());
                    Files.copy(cliFile.toPath(), testDir.resolve(cliFile.getName()));
                }
            }
            Files.copy(pom.toPath(), pomFile);
            Files.copy(clientPom.toPath(), clientPomFile);
        } else {
            File srcDir = getTestFile("src/test/resources/projects/" + projectFile);
            IoUtils.copy(srcDir.toPath(), testDir);
        }
    }

    protected Path checkAndGetWildFlyHome(Path dir, boolean expectDeployment, boolean isRoot,
                            String[] layers, String[] excludedLayers, boolean stateRecorded, String... configTokens) throws Exception {
        Path tmpDir = Files.createTempDirectory("bootable-jar-test-unzipped");
        Path wildflyHome = Files.createTempDirectory("bootable-jar-test-unzipped-" + AbstractBuildBootableJarMojo.BOOTABLE_SUFFIX);
        try {
            Path jar = dir.resolve("target").resolve(TEST_FILE);
            assertTrue(Files.exists(jar));

            ZipUtils.unzip(jar, tmpDir);
            Path zippedWildfly = tmpDir.resolve("wildfly.zip");
            assertTrue(Files.exists(zippedWildfly));

            Path provisioningFile = tmpDir.resolve("provisioning.xml");
            assertTrue(Files.exists(provisioningFile));

            ZipUtils.unzip(zippedWildfly, wildflyHome);
            checkWildFlyHome(wildflyHome, expectDeployment ? 1 : 0, isRoot, layers, excludedLayers, stateRecorded, configTokens);
        } finally {
            BuildBootableJarMojo.deleteDir(tmpDir);
        }
        return wildflyHome;
    }

    protected void checkWildFlyHome(Path wildflyHome, int numDeployments, boolean isRoot,
            String[] layers, String[] excludedLayers, boolean stateRecorded, String... configTokens) throws Exception {
        if (numDeployments > 0) {
            // Must retrieve all content directories.
            Path rootDir = wildflyHome.resolve("standalone/data/content");
            List<Path> deployments = new ArrayList<>();
            Files.walkFileTree(rootDir, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                        if ("content".equals(file.getFileName().toString())) {
                            deployments.add(file);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            assertEquals(numDeployments, deployments.size());
        } else {
            // The directory should be empty if no deployment is expected, however in some cases it may not even be
            // created.
            if (Files.exists(wildflyHome.resolve("standalone/data/content"))) {
                assertEquals(0, Files.list(wildflyHome.resolve("standalone/data/content")).count());
            }
        }
        Path history = wildflyHome.resolve("standalone").resolve("configuration").resolve("standalone_xml_history");
        assertFalse(Files.exists(history));

        Path configFile = wildflyHome.resolve("standalone/configuration/standalone.xml");
        assertTrue(Files.exists(configFile));
        if (layers != null) {
            Path provisioning = PathsUtils.getProvisioningXml(wildflyHome);
            assertTrue(Files.exists(provisioning));
            ProvisioningConfig config = ProvisioningXmlParser.parse(provisioning);
            ConfigModel cm = config.getDefinedConfig(new ConfigId("standalone", "standalone.xml"));
            assertNotNull(config.getDefinedConfigs().toString(), cm);
            assertEquals(layers.length, cm.getIncludedLayers().size());
            for (String layer : layers) {
                assertTrue(cm.getIncludedLayers().contains(layer));
            }
            if (excludedLayers != null) {
                for (String layer : excludedLayers) {
                    assertTrue(cm.getExcludedLayers().contains(layer));
                }
            }
        }
        if (configTokens != null) {
            String str = new String(Files.readAllBytes(configFile), StandardCharsets.UTF_8);
            for (String token : configTokens) {
                assertTrue(str, str.contains(token));
            }
        }
         assertEquals(Files.exists(wildflyHome.resolve(".galleon")), stateRecorded);
         assertEquals(Files.exists(wildflyHome.resolve(".wildfly-jar-plugin-provisioning.xml")), !stateRecorded);
    }

    protected void checkJar(Path dir, boolean expectDeployment, boolean isRoot,
            String[] layers, String[] excludedLayers, boolean stateRecorded, String... configTokens) throws Exception {
        Path wildflyHome = null;
        try {
            wildflyHome = checkAndGetWildFlyHome(dir, expectDeployment, isRoot, layers, excludedLayers, stateRecorded, configTokens);
        } finally {
            if (wildflyHome != null) {
                BuildBootableJarMojo.deleteDir(wildflyHome);
            }
        }
    }

    protected void checkServer(Path dir, String installDirName, int numDeployments, boolean isRoot,
            String[] layers, String[] excludedLayers, boolean stateRecorded, String... configTokens) throws Exception {
        Path wildflyHome = dir.resolve("target").resolve(installDirName);
        checkWildFlyHome(wildflyHome, numDeployments, isRoot, layers, excludedLayers, stateRecorded, configTokens);
    }

    protected void checkDeployment(Path dir, String fileName, boolean isRoot) throws Exception {
        checkDeployment(true, dir, fileName, isRoot);
    }

    protected void checkDeployment(Path dir, boolean isRoot) throws Exception {
        checkDeployment(true, dir, isRoot);
    }

    protected void checkDeployment(Path dir, boolean isRoot, String... args) throws Exception {
        checkDeployment(true, dir, isRoot, args);
    }

    protected void checkDeployment(boolean isJar, Path dir, String fileName, boolean isRoot) throws Exception {
        checkURL(isJar, dir, fileName, createUrl(TestEnvironment.getHttpPort(), isRoot ? "" : "test"), true);
    }

    protected void checkDeployment(boolean isJar, Path dir, boolean isRoot) throws Exception {
        checkURL(isJar, dir, null, createUrl(TestEnvironment.getHttpPort(), isRoot ? "" : "test"), true);
    }

    protected void checkDeployment(boolean isJar, Path dir, boolean isRoot, String... args) throws Exception {
        checkURL(isJar, dir, null, createUrl(TestEnvironment.getHttpPort(), isRoot ? "" : "test"), true, args);
    }

    protected void checkManagementItf(Path dir, boolean start) throws Exception {
        checkManagementItf(true, dir, start);
    }

    protected void checkManagementItf(boolean isJar, Path dir, boolean start) throws Exception {
        checkURL(isJar, dir, null, null, start);
    }

    protected void checkMetrics(Path dir, boolean start) throws Exception {
        checkMetrics(true, dir, start);
    }

    protected void checkMetrics(boolean isJar, Path dir, boolean start) throws Exception {
        checkURL(isJar, dir, null, createUrl(TestEnvironment.getManagementPort(), "metrics"), start);
    }

    protected void checkURL(Path dir, String fileName, String url, boolean start, String... args) throws Exception {
        checkURL(true, dir, fileName, url, start, args);
    }
    protected void checkURL(boolean isJar, Path dir, String fileName, String url, boolean start, String... args) throws Exception {
        Process process = null;
        int timeout = TestEnvironment.getTimeout() * 1000;
        long sleep = 1000;
        boolean success = false;
        try {
            if (start) {
                process = startServer(isJar, dir, fileName, args);
            }
            // Check the server state in all cases. All test cases are provisioning the manager layer.
            try (ModelControllerClient client = ModelControllerClient.Factory.create(TestEnvironment.getHost(),
                    TestEnvironment.getManagementPort())) {
                // Wait for the server to start, this calls into the management interface.
                ServerHelper.waitForStandalone(process, client, TestEnvironment.getTimeout());
            }

            if (url == null) {
                // Checking for the server state is enough.
                success = true;
            } else {
                while (timeout > 0) {
                    if (checkURL(url)) {
                        System.out.println("Successfully connected to " + url);
                        success = true;
                        break;
                    }
                    Thread.sleep(sleep);
                    timeout -= sleep;
                }
            }
            if (process != null) {
                assertTrue(process.isAlive());
            }
            shutdown();
            // If the process is not null wait for it to shutdown
            if (process != null) {
                assertTrue("The process has failed to shutdown", process.waitFor(TestEnvironment.getTimeout(), TimeUnit.SECONDS));
            }
        } finally {
            ProcessHelper.destroyProcess(process);
        }
        if (!success) {
            throw new Exception("Unable to interact with deployed application");
        }
    }

    protected String getContent(String url) throws Exception {
        int timeout = TestEnvironment.getTimeout() * 1000;
        long sleep = 1000;
        String content = null;
        while (timeout > 0) {
            if ((content = getBodyContent(url)) != null) {
                System.out.println("Successfully connected to " + url);
                break;
            }
            Thread.sleep(sleep);
            timeout -= sleep;
        }
        if (content == null) {
            throw new Exception("Unable to interact with deployed application, no content retrieved");
        }
        return content;
    }

    protected Process startServer(boolean isJar, Path dir, String fileName, String... args) throws Exception {
        List<String> cmd = isJar ? jarCommand(dir, fileName, args) : serverCommand(dir, fileName, args);
        return createProcess(cmd);
    }

    protected Process createProcess(List<String> cmd) throws Exception {
        final Path out = TestEnvironment.createTempPath("logs", getClass().getName() + "-process.txt");
        final Path parent = out.getParent();
        if (parent != null && Files.notExists(parent)) {
            Files.createDirectories(parent);
        }
        return new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .redirectOutput(out.toFile())
                .start();
    }

    protected List<String> jarCommand(Path dir, String fileName, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(getJavaCommand());
        cmd.addAll(getJvmArgs());
        cmd.add("-jar");
        cmd.add(dir.resolve("target").resolve(fileName == null ? TEST_FILE : fileName).toAbsolutePath().toString());
        cmd.addAll(Arrays.asList(args));
        return cmd;
    }

    protected List<String> serverCommand(Path dir, String fileName, String... args) throws Exception {
        StandaloneCommandBuilder builder = StandaloneCommandBuilder.of(dir.resolve("target").
                resolve(fileName == null ? SERVER_DEFAULT_DIR_NAME : fileName))
                    .addJavaOptions(getJvmArgs())
                    .addServerArguments(args);
        return builder.build();
    }

    protected boolean checkURL(String url) {
        try {
            try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
                HttpGet httpget = new HttpGet(url);

                CloseableHttpResponse response = httpclient.execute(httpget);
                System.out.println("STATUS CODE " + response.getStatusLine().getStatusCode());
                return response.getStatusLine().getStatusCode() == 200;
            }
        } catch (Exception ex) {
            System.out.println(ex);
            return false;
        }
    }

    protected String getBodyContent(String url) throws Exception {
        try (CloseableHttpClient httpclient = HttpClients.createDefault()) {
            HttpGet httpget = new HttpGet(url);

            CloseableHttpResponse response = httpclient.execute(httpget);
            System.out.println("STATUS CODE " + response.getStatusLine().getStatusCode());
            if (response.getStatusLine().getStatusCode() != 200) {
                return null;
            }
            HttpEntity entity = response.getEntity();
            return EntityUtils.toString(entity, "UTF-8");
        } catch (Exception ex) {
            System.out.println(ex);
            return null;
        }
    }

    protected boolean pollBodyContent(String url, String content) throws Exception {
        int timeout = TestEnvironment.getTimeout() * 1000;
        long sleep = 1000;
        boolean success = false;
        while (timeout > 0) {
            String remoteContent = getBodyContent(url);
            if (content.equals(remoteContent)) {
                System.out.println("Expected content returned from " + url);
                success = true;
                break;
            } else {
                System.out.println("Remote content not equals to expected");
                System.out.println("[" + content + "]");
                System.out.println("[" + remoteContent + "]");
            }
            Thread.sleep(sleep);
            timeout -= sleep;
        }
        return success;
    }

    static Path createTestDirectory(final String... paths) {
        final Path dir = TestEnvironment.createTempPath(paths);
        try {
            if (Files.exists(dir)) {
                Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                    @Override
                    public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return dir;
    }

    protected static String createUrl(final int port, final String... paths) {
        final StringBuilder result = new StringBuilder(32)
                .append("http://")
                .append(TestEnvironment.getHost())
                .append(':')
                .append(port);
        for (String path : paths) {
            result.append('/')
                    .append(path);
        }
        return result.toString();
    }

    private static String getJavaCommand() {
        String cmd = "java";
        final Path javaHome = Paths.get(System.getProperty("java.home"));
        if (Files.exists(javaHome)) {
            final Path java = javaHome.resolve("bin").resolve(TestEnvironment.isWindows() ? "java.exe" : "java");
            if (Files.exists(java)) {
                return java.toAbsolutePath().toString();
            }
        }
        return cmd;
    }

    private static Collection<String> getJvmArgs() {
        final Collection<String> result = new ArrayList<>();
        final String defaultArgs = System.getProperty("test.jvm.args");
        if (defaultArgs != null) {
            final String[] defaults = defaultArgs.split("\\s+");
            for (String arg : defaults) {
                if (arg != null && !arg.trim().isEmpty()) {
                    result.add(arg);
                }
            }
        }
        return result;
    }

    private static void shutdown() throws IOException {
        try (ModelControllerClient client = ModelControllerClient.Factory.create(TestEnvironment.getHost(), TestEnvironment.getManagementPort())) {
            if (ServerHelper.isStandaloneRunning(client)) {
                ServerHelper.shutdownStandalone(client, TestEnvironment.getTimeout());
            }
        }
    }

    private static void shutdownAsync() throws IOException {
        try (ModelControllerClient client = ModelControllerClient.Factory.create(TestEnvironment.getHost(), TestEnvironment.getManagementPort())) {
            if (ServerHelper.isStandaloneRunning(client)) {
                shutdownStandaloneAsync(client, TestEnvironment.getTimeout());
            }
        }
    }

    private static void shutdownStandaloneAsync(final ModelControllerClient client, final int timeout) throws IOException {
        final ModelNode op = Operations.createOperation("shutdown");
        op.get("timeout").set(timeout);
        client.executeAsync(op);
    }

    List<String> mvnCommand(Path pomFile, String goal, List<String> options) {
        // We can't call into the plugin, when reloading itself (after a pom.xml change
        // the clasloading context switch from AppClassloader to Maven RealmClass and all is reloaded without delegation
        // breaking fully the loading env.

        // CI has a different executable path.
        Boolean isCI = Boolean.getBoolean("org.wildfly.bootable.jar.ci.execution");
        List<String> cmd = new ArrayList<>();
        if (isWindows()) {
            if (isCI) {
                cmd.add("pwsh.EXE");
                cmd.add("-command ");
                cmd.add("mvn");
                List<String> extraOptions = new ArrayList<>();
                for(String p : options) {
                    extraOptions.add("'" + p + "'");
                }
                options = extraOptions;
            } else {
                cmd.add("mvn.cmd");
            }
        } else {
            cmd.add("mvn");
        }

        String[] mvnCmd = {"-f", pomFile.toAbsolutePath().toString(), goal, "-e"};
        cmd.addAll(Arrays.asList(mvnCmd));
        cmd.addAll(options);
        return cmd;
    }
}
