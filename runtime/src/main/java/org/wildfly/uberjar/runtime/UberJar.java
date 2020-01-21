/*
 * Copyright 2016-2020 Red Hat, Inc. and/or its affiliates
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
package org.wildfly.uberjar.runtime;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.impl.AdditionalBootCliScriptInvoker;
import static org.jboss.as.process.CommandLineConstants.ADMIN_ONLY_MODE;
import static org.jboss.as.process.CommandLineConstants.START_MODE;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.PropertyConfigurator;
import org.wildfly.core.embedded.Configuration;
import org.wildfly.core.embedded.EmbeddedProcessFactory;
import org.wildfly.core.embedded.StandaloneServer;
import static org.wildfly.uberjar.runtime.Constants.JBOSS_SERVER_CONFIG_DIR;
import static org.wildfly.uberjar.runtime.Constants.JBOSS_SERVER_LOG_DIR;
import static org.wildfly.uberjar.runtime.Constants.LOG_BOOT_FILE_PROP;
import static org.wildfly.uberjar.runtime.Constants.LOG_MANAGER_CLASS;
import static org.wildfly.uberjar.runtime.Constants.LOG_MANAGER_PROP;
import org.wildfly.uberjar.runtime._private.UberJarLogger;

/**
 *
 * @author jdenise
 */
class UberJar {

    private class ShutdownHook extends Thread {

        @Override
        public void run() {
            synchronized (UberJar.this) {
                shutdown = true;
                try {
                    log.shuttingDown();
                    // Give max 10 seconds for the server to stop before to delete jbossHome.
                    ModelNode mn = new ModelNode();
                    mn.get("address");
                    mn.get("operation").set("read-attribute");
                    mn.get("name").set("server-state");
                    for (int i = 0; i < 10; i++) {
                        try {
                            ModelControllerClient client = server.getModelControllerClient();
                            if (client != null) {
                                ModelNode ret = client.execute(mn);
                                if (ret.hasDefined("result")) {
                                    String val = ret.get("result").asString();
                                    if ("stopped".equals(val)) {
                                        log.serverStopped();
                                        break;
                                    } else {
                                        log.serverNotStopped();
                                    }
                                }
                                Thread.sleep(1000);
                            } else {
                                log.nullController();
                                break;
                            }
                        } catch (Exception ex) {
                            log.unexpectedExceptionWhileShuttingDown(ex);
                        }
                    }
                } finally {
                    cleanup();
                }
            }
        }
    }

    private static final Set<PosixFilePermission> EXECUTE_PERMISSIONS = new HashSet<>();
    static final String[] EXTENDED_SYSTEM_PKGS = new String[]{"org.jboss.logging", "org.jboss.logmanager"};

    static {
        EXECUTE_PERMISSIONS.add(PosixFilePermission.OWNER_EXECUTE);
        EXECUTE_PERMISSIONS.add(PosixFilePermission.OWNER_WRITE);
        EXECUTE_PERMISSIONS.add(PosixFilePermission.OWNER_READ);
        EXECUTE_PERMISSIONS.add(PosixFilePermission.GROUP_EXECUTE);
        EXECUTE_PERMISSIONS.add(PosixFilePermission.GROUP_WRITE);
        EXECUTE_PERMISSIONS.add(PosixFilePermission.GROUP_READ);
        EXECUTE_PERMISSIONS.add(PosixFilePermission.OTHERS_EXECUTE);
        EXECUTE_PERMISSIONS.add(PosixFilePermission.OTHERS_READ);
    }

    private UberJarLogger log;

    private Path jbossHome;
    private final List<String> startServerArgs = new ArrayList<>();
    private StandaloneServer server;
    private boolean autoConfigure;
    private Path markerDir;
    private boolean shutdown;
    private final boolean isTmpDir;
    private final Arguments arguments;
    private boolean deleteDir;

    public UberJar(Arguments arguments) throws Exception {
        this.arguments = arguments;
        deleteDir = !arguments.isNoDelete();
        if (arguments.getScriptFile() != null) {
            addCliScript(arguments.getScriptFile());
        }
        if (arguments.getServerDir() != null) {
            setJBossHome(arguments.getServerDir());
        }
        if (jbossHome == null) {
            jbossHome = Files.createTempDirectory("wildfly-uberjar-server");
            isTmpDir = true;
        } else {
            isTmpDir = false;
        }

        long t = System.currentTimeMillis();
        try ( InputStream wf = Main.class.getResourceAsStream("/wildfly.zip")) {
            unzip(wf, jbossHome.toFile());
        }

        startServerArgs.addAll(arguments.getServerArguments());
        configureLogging();

        if (arguments.getDeployment() != null) {
            Path deployment = jbossHome.resolve("standalone/deployments");
            File[] files = deployment.toFile().listFiles((f, name) -> {
                name = name.toLowerCase();
                return name.endsWith(".war") || name.endsWith(".jar") || name.endsWith(".ear");
            });
            if (files != null && files.length > 0) {
                throw new Exception("Deployment already exists not an hollow-jar");
            }
            Path target = deployment.resolve(arguments.getDeployment().getFileName());
            Files.copy(arguments.getDeployment(), target);
            log.installDeployment(arguments.getDeployment());
        }

        log.advertiseStart(jbossHome, System.currentTimeMillis() - t);

        if (arguments.getExternalConfig() != null) {
            final String baseDir = jbossHome + File.separator + "standalone";
            final String serverCfg = System.getProperty(JBOSS_SERVER_CONFIG_DIR, baseDir + File.separator
                    + "configuration" + File.separator + "standalone.xml");
            Path target = Paths.get(serverCfg);
            Files.copy(arguments.getExternalConfig(), target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void configureLogging() throws IOException {
        System.setProperty(LOG_MANAGER_PROP, LOG_MANAGER_CLASS);
        configureEmbeddedLogging();
        // Share the log context with embedded
        log = UberJarLogger.ROOT_LOGGER;
    }

    private void configureEmbeddedLogging() throws IOException {
        System.setProperty("org.wildfly.logging.embedded", "false");
        if (!arguments.isVersion()) {
            LogContext ctx = configureLogContext();
            LogContext.setLogContextSelector(() -> {
                return ctx;
            });
        }
    }

    LogContext configureLogContext() throws IOException {
        final String baseDir = jbossHome + File.separator + "standalone";
        String serverLogDir = System.getProperty(JBOSS_SERVER_LOG_DIR, null);
        if (serverLogDir == null) {
            serverLogDir = baseDir + File.separator + "log";
            System.setProperty(JBOSS_SERVER_LOG_DIR, serverLogDir);
        }
        final String serverCfgDir = System.getProperty(JBOSS_SERVER_CONFIG_DIR, baseDir + File.separator + "configuration");
        final LogContext embeddedLogContext = LogContext.create();
        final Path bootLog = Paths.get(serverLogDir).resolve("server.log");
        final Path loggingProperties = Paths.get(serverCfgDir).resolve(Paths.get("logging.properties"));
        if (Files.exists(loggingProperties)) {
            try (final InputStream in = Files.newInputStream(loggingProperties)) {
                System.setProperty(LOG_BOOT_FILE_PROP, bootLog.toAbsolutePath().toString());
                PropertyConfigurator configurator = new PropertyConfigurator(embeddedLogContext);
                configurator.configure(in);
            }
        }
        return embeddedLogContext;
    }

    private void setJBossHome(String path) throws IOException {
        jbossHome = Paths.get(path);
        if (Files.exists(jbossHome)) {
            throw new IOException("Installation directory " + path + " already exists");
        }
        Files.createDirectories(jbossHome);
    }

    private void addCliScript(Path path) throws IOException {
        startServerArgs.add(START_MODE + "=" + ADMIN_ONLY_MODE);
        startServerArgs.add("-D" + AdditionalBootCliScriptInvoker.CLI_SCRIPT_PROPERTY + "=" + path);
        markerDir = Files.createTempDirectory(null);
        startServerArgs.add("-D" + AdditionalBootCliScriptInvoker.MARKER_DIRECTORY_PROPERTY + "=" + markerDir);
        autoConfigure = true;
    }

    public void run() throws Exception {
        try {
            server = buildServer(startServerArgs);
        } catch (RuntimeException ex) {
            cleanup();
            throw ex;
        }
        Runtime.getRuntime().addShutdownHook(new ShutdownHook());
        server.start();
        checkForRestart();
    }

    private void checkForRestart() throws Exception {
        if (autoConfigure && !arguments.isVersion()) {
            while (true) {
                Path marker = markerDir.resolve("wf-restart-embedded-server");
                Path doneMarker = markerDir.resolve("wf-cli-invoker-result");
                if (Files.exists(doneMarker)) {
                    if (Files.exists(marker)) {
                        // Need to synchronize due to shutdown hook.
                        synchronized (this) {
                            if (!shutdown) {
                                log.info("Restarting server");
                                server.stop();
                                try {
                                    System.clearProperty(AdditionalBootCliScriptInvoker.CLI_SCRIPT_PROPERTY);
                                    System.clearProperty(AdditionalBootCliScriptInvoker.MARKER_DIRECTORY_PROPERTY);
                                    server = buildServer(arguments.getServerArguments());
                                } catch (RuntimeException ex) {
                                    cleanup();
                                    throw ex;
                                }
                                server.start();
                            } else {
                                log.allreadyShutdown();
                            }
                        }
                    }
                    break;
                }
                Thread.sleep(10);
            }
        }
    }

    private void cleanup() {
        if (deleteDir) {
            log.deletingHome(jbossHome);
            deleteDir(jbossHome);
            deleteDir = false;
        } else {
            if (isTmpDir) {
                log.homeNotDeleted(jbossHome);
            }
        }
        if (markerDir != null) {
            log.deletingMarkerDir(markerDir);
            deleteDir(markerDir);
        }
    }

    private StandaloneServer buildServer(List<String> args) throws IOException {
        Configuration.Builder builder = Configuration.Builder.of(jbossHome);
        builder.addSystemPackages(EXTENDED_SYSTEM_PKGS);
        for (String a : args) {
            builder.addCommandArgument(a);
        }
        builder.setUberJar(true);
        final StandaloneServer serv = EmbeddedProcessFactory.createStandaloneServer(builder.build());
        return serv;
    }

    private static void deleteDir(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    try {
                        Files.delete(file);
                    } catch (IOException ex) {
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException e)
                        throws IOException {
                    if (e != null) {
                        // directory iteration failed
                        throw e;
                    }
                    try {
                        Files.delete(dir);
                    } catch (IOException ex) {
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
        }
    }

    private static void unzip(InputStream wf, File dir) throws Exception {
        byte[] buffer = new byte[1024];
        try ( ZipInputStream zis = new ZipInputStream(wf)) {
            ZipEntry ze = zis.getNextEntry();
            while (ze != null) {
                String fileName = ze.getName();
                File newFile = new File(dir, fileName);
                if (fileName.endsWith("/")) {
                    newFile.mkdirs();
                    zis.closeEntry();
                    ze = zis.getNextEntry();
                    continue;
                }
                try ( FileOutputStream fos = new FileOutputStream(newFile)) {
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }
                if (newFile.getName().endsWith(".sh")) {
                    Files.setPosixFilePermissions(newFile.toPath(), EXECUTE_PERMISSIONS);
                }
                zis.closeEntry();
                ze = zis.getNextEntry();
            }
        }
    }
}
