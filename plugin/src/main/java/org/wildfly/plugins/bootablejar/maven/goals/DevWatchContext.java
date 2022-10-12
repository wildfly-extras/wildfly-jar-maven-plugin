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
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.ProjectBuildingException;
import org.jboss.galleon.util.IoUtils;
import static org.wildfly.plugins.bootablejar.maven.goals.AbstractBuildBootableJarMojo.JAR;
import static org.wildfly.plugins.bootablejar.maven.goals.AbstractBuildBootableJarMojo.WAR;

/**
 *
 * @author jdenise
 */
class DevWatchContext {

    private static final Set<String> NO_DEPLOYMENT_WEB_FILE_EXTENSIONS = new HashSet<>();
    static {
        NO_DEPLOYMENT_WEB_FILE_EXTENSIONS.add("xhtml");
        NO_DEPLOYMENT_WEB_FILE_EXTENSIONS.add("html");
        NO_DEPLOYMENT_WEB_FILE_EXTENSIONS.add("jsp");
        NO_DEPLOYMENT_WEB_FILE_EXTENSIONS.add("css");
    }
    interface ProjectContext {

        List<String> getWebExtensions();

        Path getBaseDir();

        Path getSourceDir();

        Path getProjectBuildDir();

        Path getDeploymentsDir();

        Set<Path> getResources();

        boolean isContextRoot();

        String getFinalName();

        boolean isPluginConfigUpdated() throws ProjectBuildingException;

        List<CliSession> getCliSessions();

        List<String> getExtraServerContent();

        void debug(String msg);

        void info(String msg);

        String getPackaging();

        void cleanup(boolean autoCompile) throws MojoExecutionException;

        void compile(boolean autoCompile) throws MojoExecutionException;

        void resources() throws MojoExecutionException;

        void packageWar(Path targetDir) throws MojoExecutionException;

        void packageJar(Path targetDir, Path artifactFile) throws IOException, MojoExecutionException;

        Path getPomFile();

        void deploy(Path targetDir) throws Exception;

        Set<Path> getCompileRoots();
    }

    abstract class BootableAppEventHandler {

        boolean rebuildBootableJAR;
        boolean compile;
        boolean redeploy;
        boolean repackage;
        boolean clean;
        boolean reset;
        boolean resources;

        // Used by tests
        Map<Path, Path> copied = new HashMap<>();
        Map<Path, Path> deleted = new HashMap<>();
        Set<Path> monitored = new HashSet<>();
        Set<Path> stopMonitored = new HashSet<>();
        Set<Path> seenUpdated = new HashSet<>();

        public void handle(Kind event, Path absolutePath) throws Exception {
            boolean isDirectory = Files.isDirectory(absolutePath);
            // On Windows we see some MODIFY events for deleted dir containing files.
            boolean fileExists = Files.exists(absolutePath);
            if (event == ENTRY_DELETE) {
                fileDeleted(absolutePath, stopMonitored);
                Path indep = getInDeploymentPath(absolutePath);
                if (indep != null) {
                    ctx.debug("[WATCH] Delete file " + indep);
                    Files.deleteIfExists(indep);
                    deleted.put(absolutePath, indep);
                    redeploy = true;
                } else {
                    ctx.debug("[WATCH] Not a deployment file " + absolutePath);
                    deleted.put(absolutePath, absolutePath);
                }

            } else {
                if (event == ENTRY_CREATE) {
                    if (isDirectory) {
                        registerDir(absolutePath, monitored);
                    }
                } else if (event == ENTRY_MODIFY) {
                    if (fileExists && !isDirectory) {
                        if (pom.equals(absolutePath)) {
                            if (ctx.isPluginConfigUpdated()) {
                                ctx.info("[WATCH] Must rebuild the bootable JAR.");
                                rebuildBootableJAR = true;
                            } else {
                                ctx.info("[WATCH] Reset the watcher.");
                                reset = true;

                            }
                            seenUpdated.add(absolutePath);
                        } else if (isBootableSpecificFile(absolutePath)) {
                            rebuildBootableJAR = true;
                            ctx.info("[WATCH] Must rebuild the bootable JAR. ");
                            seenUpdated.add(absolutePath);
                        }
                    }
                }
            }
        }

        void applyChanges() throws IOException, MojoExecutionException {
            if (compile || redeploy) {
                ctx.debug("[WATCH] updating application");
                rebuild(false, compile, repackage, redeploy, clean, resources);
            }
        }

        protected abstract Path getInDeploymentPath(Path absolutePath);

    }

    private class JavaAppEventHandler extends BootableAppEventHandler {

        @Override
        public void handle(Kind event, Path absolutePath) throws Exception {
            boolean isDirectory = Files.isDirectory(absolutePath);
            // On Windows we see some MODIFY events for deleted dir containing files.
            boolean fileExists = Files.exists(absolutePath);
            boolean handledLocally = false;
            if (event == ENTRY_MODIFY) {
                if (!isDirectory && fileExists) {
                    if (isJavaFile(absolutePath)) {
                        ctx.debug("[WATCH] java compilation roots dir updated, need to re-compile");
                        compile = true;
                        repackage = true;
                        redeploy = true;
                        seenUpdated.add(absolutePath);
                        handledLocally = true;
                    } else {
                        Path resourcesDir = getResourcesDir(absolutePath);
                        if (resourcesDir != null) {
                            ctx.debug("[WATCH] resources dir updated, need to re-deploy");
                            copyInDeployment(absolutePath, getResourcesPath().resolve(resourcesDir.relativize(absolutePath)));
                            redeploy = true;
                            resources = true;
                            handledLocally = true;
                        }
                    }
                }
            } else {
                if (event == ENTRY_DELETE) {
                    if (isJavaFile(absolutePath)) {
                        // Nothing to delete, must rebuild it.
                        ctx.debug("[WATCH] java file deleted, need to clean and re-compile");
                        compile = true;
                        repackage = true;
                        redeploy = true;
                        clean = true;
                    }
                }
            }
            if (!handledLocally) {
                super.handle(event, absolutePath);
            }
        }

        @Override
        protected Path getInDeploymentPath(Path absolutePath) {
            Path path = getResourcesDir(absolutePath);
            if (path != null) {
                return toDeploymentPath(absolutePath, getResourcesPath().resolve(path.relativize(absolutePath)));
            }
            return null;
        }

        Path toDeploymentPath(Path absolutePath, Path relativePath) {
            return targetDir.resolve(relativePath);
        }

        Path getResourcesPath() {
            return Paths.get("");
        }

        void copyInDeployment(Path absolutePath, Path relativePath) throws IOException {
            Path p = toDeploymentPath(absolutePath, relativePath);
            ctx.debug("[WATCH] copy " + absolutePath + " to " + p);
            Files.createDirectories(p.getParent());
            Files.copy(absolutePath, p, StandardCopyOption.REPLACE_EXISTING);
            copied.put(absolutePath, p);

        }
    }

    private class WebAppEventHandler extends JavaAppEventHandler {

        @Override
        public void handle(Kind event, Path absolutePath) throws Exception {
            boolean isDirectory = Files.isDirectory(absolutePath);
            // On Windows we see some MODIFY events for deleted dir containing files.
            boolean fileExists = Files.exists(absolutePath);
            boolean handledLocally = false;
            if (event == ENTRY_MODIFY || event == ENTRY_CREATE) {
                if (fileExists && !isDirectory) {
                    Path relativePath = isWebFile(absolutePath);
                    if (relativePath != null) {
                        copyInDeployment(absolutePath, relativePath);
                        redeploy = requiresRedeploy(absolutePath);
                        handledLocally = true;
                    }
                }
            }
            if (!handledLocally) {
                super.handle(event, absolutePath);
            }
        }

        private boolean requiresRedeploy(Path path) {
            if (Files.isDirectory(path)) {
                return true;
            }
            String fileName = path.getFileName().toString();
            int index = fileName.lastIndexOf(".");
            if (index != -1 && index != fileName.length() -1) {
                String extension = fileName.substring(index + 1);
                Set<String> allExtensions = new HashSet<>();
                allExtensions.addAll(NO_DEPLOYMENT_WEB_FILE_EXTENSIONS);
                allExtensions.addAll(ctx.getWebExtensions());
                if (allExtensions.contains(extension)) {
                    ctx.debug("[WATCH] Simple copy for file " + fileName);
                    return false;
                }
            }
            return true;
        }

        private Path isWebFile(Path absolutePath) {
            if (absolutePath.startsWith(webAppDir)) {
                return webAppDir.relativize(absolutePath);
            }
            return null;
        }

        @Override
        protected Path getInDeploymentPath(Path absolutePath) {
            if (absolutePath.startsWith(webAppDir)) {
                return toDeploymentPath(absolutePath, webAppDir.relativize(absolutePath));
            } else {
                return super.getInDeploymentPath(absolutePath);
            }
        }

        @Override
        Path getResourcesPath() {
            return Paths.get("WEB-INF").resolve("classes");
        }

    }
    private final Map<WatchKey, Path> watchedDirectories = new HashMap<>();
    private final Path webAppDir;
    private final Set<Path> resourceDirectories = new HashSet<>();
    private final Set<Path> compileRootDirectories;
    private final Set<Path> extraDirectories = new HashSet<>();
    private final Set<Path> cliFiles = new HashSet<>();
    private final Path pom;
    private final WatchService watcher;
    private final Path projectBuildDir;
    private final Path artifactFile;
    private final boolean isWebApp;
    private final boolean isJarApp;
    private final String fileName;
    private final Path targetDir;
    private final ProjectContext ctx;

    DevWatchContext(ProjectContext ctx,
            WatchService watcher) throws IOException, MojoExecutionException {
        this.watcher = watcher;
        this.ctx = ctx;
        this.projectBuildDir = ctx.getProjectBuildDir();
        Path mainDir = ctx.getSourceDir().resolve("main");
        this.compileRootDirectories = ctx.getCompileRoots();
        webAppDir = mainDir.resolve("webapp");

        String finalName = ctx.getFinalName();
        artifactFile = projectBuildDir.resolve(finalName + "." + ctx.getPackaging());
        if (!Files.exists(ctx.getDeploymentsDir())) {
            Files.createDirectories(ctx.getDeploymentsDir());
        }
        String fName = artifactFile.getFileName().toString();
        boolean webapp = false;
        boolean jarapp = false;
        if (ctx.getPackaging().equals(WAR) || fName.endsWith(WAR)) {
            if (ctx.isContextRoot()) {
                fName = "ROOT." + WAR;
            }
            webapp = true;
        } else {
            if (ctx.getPackaging().equals(JAR) || fName.endsWith(JAR)) {
                jarapp = true;
            } else {
                throw new RuntimeException("Not supported packaging : " + ctx.getPackaging());
            }
        }
        isWebApp = webapp;
        isJarApp = jarapp;
        fileName = fName;
        targetDir = ctx.getDeploymentsDir().resolve(fileName);

        registerDir(ctx.getBaseDir());

        pom = ctx.getPomFile();

        for (Path p : ctx.getResources()) {
            if (!p.isAbsolute()) {
                p = ctx.getBaseDir().resolve(p);
            }
            // We must add it even if it doesn't exist.
            // That way we know the resources files in case they are created later.
            resourceDirectories.add(p);
            ctx.debug("[WATCH] resources dir: " + p);
            if (Files.exists(p)) {
                registerDir(p);
            }
        }

        for (String extra : ctx.getExtraServerContent()) {
            Path p = Paths.get(extra);
            if (!p.isAbsolute()) {
                p = ctx.getBaseDir().resolve(p);
            }
            extraDirectories.add(p);
            ctx.debug("[WATCH] extra-content dir: " + p);
            registerDir(p);
        }

        for (CliSession session : ctx.getCliSessions()) {
            for (String f : session.getScriptFiles()) {
                Path p = Paths.get(f);
                if (!p.isAbsolute()) {
                    p = ctx.getBaseDir().resolve(p);
                }
                cliFiles.add(p);
                ctx.debug("[WATCH] CLI script File: " + p);
                watchedDirectories.put(p.getParent().register(watcher, ENTRY_MODIFY), p.getParent());
            }
            if (session.getPropertiesFile() != null) {
                Path p = Paths.get(session.getPropertiesFile());
                if (!p.isAbsolute()) {
                    p = ctx.getBaseDir().resolve(p);
                }
                cliFiles.add(p);
                ctx.debug("[WATCH] CLI properties File: " + p);
                watchedDirectories.put(p.getParent().register(watcher, ENTRY_MODIFY), p.getParent());
            }
        }
    }

    Path getTargetDirectory() {
        return targetDir;
    }

    private void fileDeleted(Path absolutePath, Set<Path> paths) {
        WatchKey key = null;
        for (Entry<WatchKey, Path> entry : watchedDirectories.entrySet()) {
            if (entry.getValue().equals(absolutePath)) {
                key = entry.getKey();
                break;
            }
        }
        if (key != null) {
            ctx.debug("[WATCH] cancelling monitoring of " + absolutePath);
            paths.add(absolutePath);
            key.cancel();
        }
    }

    private boolean isJavaFile(Path absolutePath) {
        if (absolutePath.getFileName().toString().endsWith(".java")) {
            for (Path javaDir : compileRootDirectories) {
                if (absolutePath.startsWith(javaDir)) {
                    return true;
                }
            }
        }
        return false;
    }

    Path getPath(WatchKey key, Path fileName) {
        Path p = watchedDirectories.get(key);
        if (p == null) {
            ctx.debug("No more watching key, ignoring change done to " + fileName);
            return null;
        } else {
            Path resolved = p.resolve(fileName);
            // Fully ignore target dir
            if (projectBuildDir.equals(resolved)) {
                return null;
            }
            return resolved;
        }

    }

    private Path getResourcesDir(Path p) {
        for (Path path : resourceDirectories) {
            if (p.startsWith(path)) {
                return path;
            }
        }
        if (!isJavaFile(p)) {
            for (Path javaDir : compileRootDirectories) {
                if (p.startsWith(javaDir)) {
                    return javaDir;
                }
            }
        }
        return null;
    }

    private boolean isBootableSpecificFile(Path p) {
        if (cliFiles.contains(p)) {
            return true;
        }
        for (Path path : extraDirectories) {
            if (p.startsWith(path)) {
                return true;
            }
        }
        return false;
    }

    private void registerDir(Path dir) throws IOException {
        registerDir(dir, null);
    }

    private void registerDir(Path dir, Set<Path> set) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (!projectBuildDir.equals(dir)) {
                    watchedDirectories.put(dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY), dir);
                    ctx.debug("[WATCH] watching " + dir);
                    if (set != null) {
                        set.add(dir);
                    }
                    return FileVisitResult.CONTINUE;
                } else {
                    return FileVisitResult.SKIP_SUBTREE;
                }
            }
        });
    }

    void cleanup() {
        for (WatchKey k : watchedDirectories.keySet()) {
            k.cancel();
        }
        if (Files.exists(getTargetDirectory())) {
            IoUtils.recursiveDelete(getTargetDirectory());
        }
    }

    BootableAppEventHandler newEventHandler() {
        BootableAppEventHandler handler;
        if (isWebApp) {
            handler = new WebAppEventHandler();
        } else {
            if (isJarApp) {
                handler = new JavaAppEventHandler();
            } else {
                throw new RuntimeException("Not supported packaging");
            }
        }
        return handler;
    }

    void build(boolean autoCompile) throws IOException, MojoExecutionException {
        rebuild(autoCompile, true, true, true, true, true);
    }

    private void rebuild(boolean autoCompile, boolean compile, boolean repackage, boolean redeploy, boolean cleanup, boolean resources) throws IOException, MojoExecutionException {
        if (cleanup) {
            ctx.cleanup(autoCompile);
        }
        if (compile || cleanup) {
            ctx.compile(autoCompile);
        }
        if (resources) {
            ctx.resources();
        }
        if (repackage || cleanup) {

            ctx.debug("[WATCH] re-package");
            if (!Files.exists(ctx.getDeploymentsDir())) {
                Files.createDirectories(ctx.getDeploymentsDir());
            }
            if (isWebApp) {
                ctx.packageWar(targetDir);
            } else {
                if (isJarApp) {
                    ctx.packageJar(targetDir, artifactFile);
                }
            }
        }
        if (redeploy || cleanup) {
            ctx.debug("[WATCH] re-deploy");
            try {
                ctx.deploy(getTargetDirectory());
            } catch (Exception ex) {
                throw new MojoExecutionException(ex.toString(), ex);
            }
        }
    }
}
