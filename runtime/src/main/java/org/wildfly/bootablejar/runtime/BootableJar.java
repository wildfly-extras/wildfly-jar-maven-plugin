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
package org.wildfly.bootablejar.runtime;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.process.CommandLineConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.PropertyConfigurator;
import org.wildfly.core.embedded.Configuration;
import org.wildfly.core.embedded.EmbeddedProcessFactory;
import org.wildfly.core.embedded.StandaloneServer;
import static org.wildfly.bootablejar.runtime.Constants.JBOSS_SERVER_CONFIG_DIR;
import static org.wildfly.bootablejar.runtime.Constants.JBOSS_SERVER_LOG_DIR;
import static org.wildfly.bootablejar.runtime.Constants.LOG_BOOT_FILE_PROP;
import static org.wildfly.bootablejar.runtime.Constants.LOG_MANAGER_CLASS;
import static org.wildfly.bootablejar.runtime.Constants.LOG_MANAGER_PROP;
import org.wildfly.bootablejar.runtime._private.BootableJarLogger;
import org.wildfly.core.launcher.Launcher;
import org.wildfly.core.launcher.StandaloneCommandBuilder;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
/**
 *
 * @author jdenise
 */
class BootableJar {

    private static final String DEP_1 = "ff";
    private static final String DEP_2 = "00";

    private class ShutdownHook extends Thread {

        @Override
        public void run() {
            log.shuttingDown();
            if (!isLaunch) {
                shutdown();
            } else {
                if (process.isAlive()) {
                    process.destroy();
                }
                cleanup();
            }
        }
    }

    static final String[] EXTENDED_SYSTEM_PKGS = new String[]{"org.jboss.logging", "org.jboss.logmanager"};

    private BootableJarLogger log;

    private Path jbossHome;
    private final List<String> startServerArgs = new ArrayList<>();
    private StandaloneServer server;
    private final Arguments arguments;
    private Process process;
    private boolean isLaunch;

    public BootableJar(Arguments arguments) throws Exception {
        this.isLaunch = Boolean.getBoolean("launch");
        this.arguments = arguments;
        jbossHome = arguments.installDir() == null ? Files.createTempDirectory("wildfly-bootable-server") : arguments.installDir();

        long t = System.currentTimeMillis();
        try (InputStream wf = Main.class.getResourceAsStream("/wildfly.zip")) {
            unzip(wf, jbossHome.toFile());
        }

        addDefaultArguments();
        startServerArgs.addAll(arguments.getServerArguments());
        startServerArgs.add(CommandLineConstants.READ_ONLY_SERVER_CONFIG + "=standalone.xml");

        configureLogging();

        if (arguments.getDeployment() != null) {
            Path deploymentDir = jbossHome.resolve("standalone/data/content/" + DEP_1 + "/" + DEP_2);

            Path target = deploymentDir.resolve("content");
            Files.createDirectories(deploymentDir);
            // Exploded deployment
            boolean isExploded = Files.isDirectory(arguments.getDeployment());
            updateConfig(jbossHome.resolve("standalone/configuration/standalone.xml"),
                    arguments.getDeployment().getFileName().toString(), isExploded);
            if (isExploded) {
                copyDirectory(arguments.getDeployment(), target);
            } else {
                Files.copy(arguments.getDeployment(), target);
            }
            log.installDeployment(arguments.getDeployment());
        }

        log.advertiseInstall(jbossHome, System.currentTimeMillis() - t);
    }

    private static void updateConfig(Path configFile, String name, boolean isExploded) throws Exception {
        FileInputStream fileInputStream = new FileInputStream(configFile.toFile());
        DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();

        Document document = documentBuilder.parse(fileInputStream);
        Element root = document.getDocumentElement();

        NodeList lst = root.getChildNodes();
        for (int i = 0; i < lst.getLength(); i++) {
            Node n = lst.item(i);
            if (n instanceof Element) {
                if ("deployments".equals(n.getNodeName())) {
                    throw BootableJarLogger.ROOT_LOGGER.deploymentAlreadyExist();
                }
            }
        }
        Element deployments = document.createElement("deployments");
        Element deployment = document.createElement("deployment");
        Element content = document.createElement("content");
        content.setAttribute("sha1", DEP_1 + DEP_2);
        if (isExploded) {
            content.setAttribute("archive", "false");
        }
        deployment.appendChild(content);
        deployment.setAttribute("name", name);
        deployment.setAttribute("runtime-name", name);
        deployments.appendChild(deployment);

        root.appendChild(deployments);
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        StreamResult output = new StreamResult(configFile.toFile());
        DOMSource input = new DOMSource(document);

        transformer.transform(input, output);

    }

    private void copyDirectory(Path src, Path target) throws IOException {
        Files.walk(src).forEach(file -> {
            try {
                Path targetFile = target.resolve(src.relativize(file));
                if (Files.isDirectory(file)) {
                    if (!Files.exists(targetFile)) {
                        Files.createDirectory(targetFile);
                    }
                } else {
                    Files.copy(file, targetFile);
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
    }

    private void addDefaultArguments() {
        if (!isLaunch) {
            startServerArgs.add("-Djava.net.preferIPv4Stack=true");
            startServerArgs.add("-Djava.awt.headless=true");
            startServerArgs.add("-Djboss.modules.system.pkgs=org.jboss.byteman");
        }
    }

    private void configureLogging() throws IOException {
        if (!isLaunch) {
            System.setProperty(LOG_MANAGER_PROP, LOG_MANAGER_CLASS);
            configureEmbeddedLogging();
        }
        // Share the log context with embedded
        log = BootableJarLogger.ROOT_LOGGER;
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

    public void run() throws Exception {
        try {
            if (!isLaunch) {
                server = buildServer(startServerArgs);
            } else {
                Runtime.getRuntime().addShutdownHook(new ShutdownHook());
                startServerProcess();
            }
        } catch (RuntimeException ex) {
            cleanup();
            throw ex;
        }

        if (!isLaunch) {
            Runtime.getRuntime().addShutdownHook(new ShutdownHook());
//            BiConsumer<Integer, Throwable> cons = (status, exx) -> {
//                server.stop();
//                if (status == ExitCodes.RESTART_PROCESS_FROM_STARTUP_SCRIPT) {
//                    try {
//                        server = buildServer(startServerArgs);
//                        server.start();
//                    } catch (Exception ex) {
//                        cleanup();
//                    }
//                } else {
//                    System.exit(status);
//                }
//
//            };
//            server.start(cons);
            server.start();
        }
    }

    private void cleanup() {
        log.deletingHome(jbossHome);
        deleteDir(jbossHome);

    }

    private void startServerProcess() throws IOException, InterruptedException {
        StandaloneCommandBuilder builder = StandaloneCommandBuilder.of(jbossHome);
        builder.addServerArguments(startServerArgs);
        System.out.println("Launching server: " + builder.build());
        Launcher launcher = Launcher.of(builder);
        process = launcher.redirectError(ProcessBuilder.Redirect.INHERIT).redirectOutput(ProcessBuilder.Redirect.INHERIT).
                addEnvironmentVariables(System.getenv()).launch();
        process.waitFor();
    }

    private StandaloneServer buildServer(List<String> args) throws IOException {
        Configuration.Builder builder = Configuration.Builder.of(jbossHome);
        // XXX TO REMOVE, to debug logging.
        boolean b = Boolean.getBoolean("disable.ext.packages");
        if (!b) {
            builder.addSystemPackages(EXTENDED_SYSTEM_PKGS);
        }
        for (String a : args) {
            builder.addCommandArgument(a);
        }
        log.advertiseOptions(args);
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
        try (ZipInputStream zis = new ZipInputStream(wf)) {
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
                try (FileOutputStream fos = new FileOutputStream(newFile)) {
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                }
                zis.closeEntry();
                ze = zis.getNextEntry();
            }
        }
    }

    private void shutdown() {
        try {
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
