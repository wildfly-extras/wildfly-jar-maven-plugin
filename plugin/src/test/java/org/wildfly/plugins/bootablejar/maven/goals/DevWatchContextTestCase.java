/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2020 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.plugins.bootablejar.maven.goals;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.jboss.galleon.util.IoUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.plugins.bootablejar.maven.goals.DevWatchContext.BootableAppEventHandler;

public class DevWatchContextTestCase {

    private static WatchService watcher;

    interface Checker {

        boolean isOk(BootableAppEventHandler handler);
    }

    private class Project {

        private final Path baseDir;
        private final Path srcDir;
        private final Path javaDir;
        private final Path pomFile;
        private final Path mainDir;
        private final Path resourcesDir;
        private final Path webAppDir;
        private final Path webinfDir;
        private final Path deploymentsDir;
        private final Path buildDir;
        private final Set<Path> resources = new HashSet<>();

        private Project(boolean hasWebApp, boolean hasResources, Set<Path> resources) throws IOException {
            baseDir = Files.createTempDirectory("testwatchdir-");
            pomFile = Files.createFile(baseDir.resolve("pom.xml"));
            Files.write(pomFile, "<!-- An empty pom file -->".getBytes());
            srcDir = Files.createDirectories(baseDir.resolve("src"));
            mainDir = Files.createDirectories(srcDir.resolve("main"));
            javaDir = Files.createDirectories(mainDir.resolve("java"));
            buildDir = Files.createDirectories(baseDir.resolve("target"));
            deploymentsDir = Files.createDirectories(buildDir.resolve("deployments"));
            if (hasResources) {
                resourcesDir = Files.createDirectories(mainDir.resolve("resources"));
                this.resources.add(resourcesDir);
            } else {
                resourcesDir = null;
            }
            if (hasWebApp) {
                webAppDir = mainDir.resolve("webapp");
                webinfDir = webAppDir.resolve("WEB-INF");
                Files.createDirectories(webinfDir);
            } else {
                webAppDir = null;
                webinfDir = null;
            }
            if (resources != null) {
                this.resources.addAll(resources);
            }
        }

        void cleanup() {
            IoUtils.recursiveDelete(baseDir);
        }
    }

    @Before
    public void init() throws Exception {
        // Recreate a watcher before each test to avoid
        // events to be mixed (Windows platform).
        if (watcher != null) {
            watcher.close();
        }
        watcher = FileSystems.getDefault().newWatchService();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("windows");
    }

    @Test
    public void testUpdateWebAppsFiles() throws Exception {
        Project project = new Project(true, false, null);
        DevWatchContext ctx = null;
        try {
            Path existingFile = Files.createFile(project.webAppDir.resolve("index.html"));
            TestProjectContext projCtx = newWebProjectContext(project, "testweb1");

            ctx = new DevWatchContext(projCtx, watcher);
            Path targetDir = project.deploymentsDir.resolve("ROOT.war");
            // Add a new file
            Path newFile = Files.createFile(project.webAppDir.resolve("index2.html"));
            Path expectedTargetFile = targetDir.resolve(newFile.getFileName());
            Files.write(newFile, "Hello".getBytes());
            BootableAppEventHandler handler = checkEvent(ctx, projCtx, false, false, false, false, false, new Checker() {
                @Override
                public boolean isOk(BootableAppEventHandler handler) {
                    return handler.copied.size() == 1;
                }
            });
            Assert.assertEquals(1, handler.copied.size());
            Assert.assertEquals(handler.copied.toString(), expectedTargetFile, handler.copied.get(newFile));
            Assert.assertEquals("Hello", new String(Files.readAllBytes(expectedTargetFile)));
            projCtx.reset();

            // Update an existing file
            Files.write(existingFile, "Hello2".getBytes());

            handler = checkEvent(ctx, projCtx, false, false, false, false, false, new Checker() {
                @Override
                public boolean isOk(BootableAppEventHandler handler) {
                    return handler.copied.size() == 1;
                }
            });
            expectedTargetFile = targetDir.resolve(existingFile.getFileName());
            Assert.assertEquals(1, handler.copied.size());
            Assert.assertEquals(expectedTargetFile, handler.copied.get(existingFile));
            Assert.assertEquals("Hello2", new String(Files.readAllBytes(expectedTargetFile)));
            projCtx.reset();

            // Delete the existing file
            // Re-create update the existing file (ODO like).
            Files.delete(existingFile);
            Files.createFile(existingFile);
            Files.write(existingFile, "Hello4".getBytes());
            handler = checkEvent(ctx, projCtx, false, false, false, false, false, new Checker() {
                @Override
                public boolean isOk(BootableAppEventHandler handler) {
                    return handler.copied.size() == 1 && handler.deleted.size() == 1;
                }
            });
            expectedTargetFile = targetDir.resolve(existingFile.getFileName());
            Assert.assertEquals(1, handler.deleted.size());
            Assert.assertEquals(expectedTargetFile, handler.deleted.get(existingFile));
            Assert.assertEquals(1, handler.copied.size());
            Assert.assertEquals(expectedTargetFile, handler.copied.get(existingFile));
            Assert.assertEquals("Hello4", new String(Files.readAllBytes(expectedTargetFile)));
            projCtx.reset();

            // Delete the existing file
            Files.delete(existingFile);
            handler = checkEvent(ctx, projCtx, false, false, false, false, false, new Checker() {
                @Override
                public boolean isOk(BootableAppEventHandler handler) {
                    return handler.deleted.size() == 1;
                }
            });
            expectedTargetFile = targetDir.resolve(existingFile.getFileName());
            Assert.assertEquals(1, handler.deleted.size());
            Assert.assertEquals(expectedTargetFile, handler.deleted.get(existingFile));
            Assert.assertFalse(Files.exists(expectedTargetFile));
            projCtx.reset();

            //Create a new directory and sub directory
            Path newDir1 = project.webAppDir.resolve("foo");
            Path newDir2 = newDir1.resolve("bar");
            Files.createDirectories(newDir2);
            handler = checkEvent(ctx, projCtx, false, false, false, false, false, new Checker() {
                @Override
                public boolean isOk(BootableAppEventHandler handler) {
                    return handler.monitored.size() == 2;
                }
            });
            Assert.assertEquals(2, handler.monitored.size());
            Assert.assertTrue(handler.monitored.contains(newDir1));
            Assert.assertTrue(handler.monitored.toString(), handler.monitored.contains(newDir2));
            projCtx.reset();

            // Add a new file in the new sub directory
            newFile = Files.createFile(newDir2.resolve("index3.html"));
            expectedTargetFile = targetDir.resolve(project.webAppDir.relativize(newFile));
            Files.write(newFile, "Hello3".getBytes());
            handler = checkEvent(ctx, projCtx, false, false, false, false, false, new Checker() {
                @Override
                public boolean isOk(BootableAppEventHandler handler) {
                    return handler.copied.size() == 1;
                }
            });
            Assert.assertEquals(1, handler.copied.size());
            Assert.assertEquals(handler.copied.toString(), expectedTargetFile, handler.copied.get(newFile));
            Assert.assertEquals("Hello3", new String(Files.readAllBytes(expectedTargetFile)));
            projCtx.reset();

            // Delete directory, random failure on Windows.
            if (!isWindows()) {
                IoUtils.recursiveDelete(newDir1);
                handler = checkEvent(ctx, projCtx, false, false, false, false, false, new Checker() {
                    @Override
                    public boolean isOk(BootableAppEventHandler handler) {
                        return handler.deleted.size() == 3;
                    }
                });
                Assert.assertEquals(3, handler.deleted.size());
                Assert.assertEquals(2, handler.stopMonitored.size());
                Assert.assertTrue(handler.stopMonitored.contains(newDir1));
                Assert.assertTrue(handler.stopMonitored.contains(newDir2));
                Assert.assertEquals(expectedTargetFile, handler.deleted.get(newFile));
                Assert.assertEquals(expectedTargetFile.getParent(), handler.deleted.get(newDir2));
                Assert.assertEquals(expectedTargetFile.getParent().getParent(), handler.deleted.get(newDir1));
                Assert.assertFalse(Files.exists(expectedTargetFile));
                projCtx.reset();
            }

            // Create an empty file in the WEB-INF directory must re-deploy
            newFile = Files.createFile(project.webinfDir.resolve("web.xml"));
            expectedTargetFile = targetDir.resolve(project.webinfDir.getFileName()).resolve(newFile.getFileName());
            handler = checkEvent(ctx, projCtx, false, false, false, false, false, new Checker() {
                @Override
                public boolean isOk(BootableAppEventHandler handler) {
                    return handler.copied.size() == 1;
                }
            });
            Assert.assertEquals(1, handler.copied.size());
            Assert.assertEquals(expectedTargetFile, handler.copied.get(newFile));
            projCtx.reset();
        } finally {
            if (ctx != null) {
                ctx.cleanup();
            }
            project.cleanup();
        }
    }

    @Test
    public void testUpdateJavaFilesWeb() throws Exception {
        Project project = new Project(true, true, null);
        DevWatchContext ctx = null;
        try {
            Path existingFile = Files.createFile(project.javaDir.resolve("Foo.java"));
            TestProjectContext projCtx = newWebProjectContext(project, "testweb2");

            ctx = new DevWatchContext(projCtx, watcher);
            // Add a new file
            Path newFile = Files.createFile(project.javaDir.resolve("Foo2.java"));
            Files.write(newFile, "Hello".getBytes());
            BootableAppEventHandler handler = checkEvent(ctx, projCtx, false, true, false, false, true, new Checker() {
                @Override
                public boolean isOk(BootableAppEventHandler handler) {
                    return handler.seenUpdated.size() == 1;
                }
            });
            Assert.assertTrue(handler.redeploy);
            Assert.assertTrue(handler.copied.isEmpty());
            Assert.assertTrue(handler.seenUpdated.contains(newFile));
            projCtx.reset();

            // Update an existing file
            Files.write(existingFile, "Hello2".getBytes());

            handler = checkEvent(ctx, projCtx, false, true, false, false, true, new Checker() {
                @Override
                public boolean isOk(BootableAppEventHandler handler) {
                    return handler.seenUpdated.size() == 1;
                }
            });
            Assert.assertTrue(handler.redeploy);
            Assert.assertTrue(handler.copied.isEmpty());
            Assert.assertTrue(handler.seenUpdated.contains(existingFile));
            projCtx.reset();

            // Delete the existing file
            // Re-create update the existing file (ODO like).
            Files.delete(existingFile);
            Files.createFile(existingFile);
            Files.write(existingFile, "Hello4".getBytes());
            handler = checkEvent(ctx, projCtx, true, true, false, false, true, new Checker() {
                @Override
                public boolean isOk(BootableAppEventHandler handler) {
                    return handler.deleted.size() == 1 && handler.seenUpdated.size() == 1;
                }
            });
            Assert.assertTrue(handler.redeploy);
            Assert.assertEquals(1, handler.deleted.size());
            Assert.assertEquals(existingFile, handler.deleted.get(existingFile));
            Assert.assertTrue(handler.copied.isEmpty());
            Assert.assertTrue(handler.seenUpdated.contains(existingFile));
            projCtx.reset();

            // Delete the existing file
            Files.delete(existingFile);
            handler = checkEvent(ctx, projCtx, true, true, false, false, true, new Checker() {
                @Override
                public boolean isOk(BootableAppEventHandler handler) {
                    return handler.deleted.size() == 1;
                }
            });
            Assert.assertTrue(handler.redeploy);
            Assert.assertEquals(1, handler.deleted.size());
            Assert.assertEquals(existingFile, handler.deleted.get(existingFile));
            projCtx.reset();

            //Create a new directory and sub directory
            Path newDir1 = project.javaDir.resolve("foo");
            Path newDir2 = newDir1.resolve("bar");
            Files.createDirectories(newDir2);
            handler = checkEvent(ctx, projCtx, false, false, false, false, false, new Checker() {
                @Override
                public boolean isOk(BootableAppEventHandler handler) {
                    return handler.monitored.size() == 2;
                }
            });
            Assert.assertFalse(handler.redeploy);
            Assert.assertEquals(2, handler.monitored.size());
            Assert.assertTrue(handler.monitored.contains(newDir1));
            Assert.assertTrue(handler.monitored.toString(), handler.monitored.contains(newDir2));
            projCtx.reset();

            // Add a new file in the new sub directory
            newFile = Files.createFile(newDir2.resolve("Foo4.java"));
            Files.write(newFile, "Hello3".getBytes());
            handler = checkEvent(ctx, projCtx, false, true, false, false, true, new Checker() {
                @Override
                public boolean isOk(BootableAppEventHandler handler) {
                    return handler.seenUpdated.size() == 1;
                }
            });
            Assert.assertTrue(handler.redeploy);
            Assert.assertTrue(handler.copied.isEmpty());
            Assert.assertTrue(handler.seenUpdated.contains(newFile));
            projCtx.reset();

            // Delete directory, random failure on Windows.
            if (!isWindows()) {
                IoUtils.recursiveDelete(newDir1);
                handler = checkEvent(ctx, projCtx, true, true, false, false, true, new Checker() {
                    @Override
                    public boolean isOk(BootableAppEventHandler handler) {
                        return handler.deleted.size() == 3;
                    }
                });
                Assert.assertTrue(handler.redeploy);
                Assert.assertEquals(3, handler.deleted.size());
                Assert.assertEquals(2, handler.stopMonitored.size());
                Assert.assertTrue(handler.stopMonitored.contains(newDir1));
                Assert.assertTrue(handler.stopMonitored.contains(newDir2));
                Assert.assertEquals(newFile, handler.deleted.get(newFile));
            }
            projCtx.reset();

            // Resources in java dir.
            // Add a new file
            Path targetDir = project.deploymentsDir.resolve("ROOT.war");
            Path newResourceFile = Files.createFile(project.javaDir.resolve("foo2.properties"));
            Files.write(newResourceFile, "Hello".getBytes());
            handler = checkEvent(ctx, projCtx, false, false, false, false, false, new Checker() {
                @Override
                public boolean isOk(BootableAppEventHandler handler) {
                    return handler.copied.size() == 1;
                }
            });
            Assert.assertTrue(handler.redeploy);
            Assert.assertEquals(1, handler.copied.size());
            Path expectedTargetFile = targetDir.resolve("WEB-INF").resolve("classes").resolve(project.javaDir.relativize(newResourceFile));
            Assert.assertEquals(expectedTargetFile, handler.copied.get(newResourceFile));
            projCtx.reset();

            // Resources in resources dir.
            // Add a new file
            Path newResourceFile2 = Files.createFile(project.resourcesDir.resolve("foo3.properties"));
            Files.write(newResourceFile2, "Hello".getBytes());
            handler = checkEvent(ctx, projCtx, false, false, false, false, false, new Checker() {
                @Override
                public boolean isOk(BootableAppEventHandler handler) {
                    return handler.copied.size() == 1;
                }
            });
            Assert.assertTrue(handler.redeploy);
            Assert.assertEquals(1, handler.copied.size());
            expectedTargetFile = targetDir.resolve("WEB-INF").resolve("classes").resolve(project.resourcesDir.relativize(newResourceFile2));
            Assert.assertEquals(expectedTargetFile, handler.copied.get(newResourceFile2));
            projCtx.reset();
        } finally {
            if (ctx != null) {
                ctx.cleanup();
            }
            project.cleanup();
        }
    }

    @Test
    public void testUpdateJavaFiles() throws Exception {
        Project project = new Project(true, true, null);
        DevWatchContext ctx = null;
        try {
            Path existingFile = Files.createFile(project.javaDir.resolve("Foo.java"));
            TestProjectContext projCtx = newJarProjectContext(project, "testjava1");

            ctx = new DevWatchContext(projCtx, watcher);
            // Add a new file
            Path newFile = Files.createFile(project.javaDir.resolve("Foo2.java"));
            Files.write(newFile, "Hello".getBytes());
            BootableAppEventHandler handler = checkEvent(ctx, projCtx, false, true, false, true, false, new Checker() {
                @Override
                public boolean isOk(BootableAppEventHandler handler) {
                    return handler.seenUpdated.size() == 1;
                }
            });
            Assert.assertTrue(handler.redeploy);
            Assert.assertTrue(handler.copied.isEmpty());
            Assert.assertTrue(handler.seenUpdated.contains(newFile));
            projCtx.reset();

            // Update an existing file
            Files.write(existingFile, "Hello2".getBytes());

            handler = checkEvent(ctx, projCtx, false, true, false, true, false, new Checker() {
                @Override
                public boolean isOk(BootableAppEventHandler handler) {
                    return handler.seenUpdated.size() == 1;
                }
            });
            Assert.assertTrue(handler.redeploy);
            Assert.assertTrue(handler.copied.isEmpty());
            Assert.assertTrue(handler.seenUpdated.contains(existingFile));
            projCtx.reset();

            // Delete the existing file
            // Re-create update the existing file (ODO like).
            Files.delete(existingFile);
            Files.createFile(existingFile);
            Files.write(existingFile, "Hello4".getBytes());
            handler = checkEvent(ctx, projCtx, true, true, false, true, false, new Checker() {
                @Override
                public boolean isOk(BootableAppEventHandler handler) {
                    return handler.deleted.size() == 1 && handler.seenUpdated.size() == 1;
                }
            });
            Assert.assertTrue(handler.redeploy);
            Assert.assertEquals(1, handler.deleted.size());
            Assert.assertEquals(existingFile, handler.deleted.get(existingFile));
            Assert.assertTrue(handler.copied.isEmpty());
            Assert.assertTrue(handler.seenUpdated.contains(existingFile));
            projCtx.reset();

            // Delete the existing file
            Files.delete(existingFile);
            handler = checkEvent(ctx, projCtx, true, true, false, true, false, new Checker() {
                @Override
                public boolean isOk(BootableAppEventHandler handler) {
                    return handler.deleted.size() == 1;
                }
            });
            Assert.assertTrue(handler.redeploy);
            Assert.assertEquals(1, handler.deleted.size());
            Assert.assertEquals(existingFile, handler.deleted.get(existingFile));
            projCtx.reset();

            //Create a new directory and sub directory
            Path newDir1 = project.javaDir.resolve("foo");
            Path newDir2 = newDir1.resolve("bar");
            Files.createDirectories(newDir2);
            handler = checkEvent(ctx, projCtx, false, false, false, false, false, new Checker() {
                @Override
                public boolean isOk(BootableAppEventHandler handler) {
                    return handler.monitored.size() == 2;
                }
            });
            Assert.assertFalse(handler.redeploy);
            Assert.assertEquals(2, handler.monitored.size());
            Assert.assertTrue(handler.monitored.contains(newDir1));
            Assert.assertTrue(handler.monitored.toString(), handler.monitored.contains(newDir2));
            projCtx.reset();

            // Add a new file in the new sub directory
            newFile = Files.createFile(newDir2.resolve("Foo4.java"));
            Files.write(newFile, "Hello3".getBytes());
            handler = checkEvent(ctx, projCtx, false, true, false, true, false, new Checker() {
                @Override
                public boolean isOk(BootableAppEventHandler handler) {
                    return handler.seenUpdated.size() == 1;
                }
            });
            Assert.assertTrue(handler.redeploy);
            Assert.assertTrue(handler.copied.isEmpty());
            Assert.assertTrue(handler.seenUpdated.contains(newFile));
            projCtx.reset();

            // Delete directory, random failure on Windows.
            if (!isWindows()) {
                IoUtils.recursiveDelete(newDir1);
                handler = checkEvent(ctx, projCtx, true, true, false, true, false, new Checker() {
                    @Override
                    public boolean isOk(BootableAppEventHandler handler) {
                        return handler.deleted.size() == 3;
                    }
                });
                Assert.assertTrue(handler.redeploy);
                Assert.assertEquals(3, handler.deleted.size());
                Assert.assertEquals(2, handler.stopMonitored.size());
                Assert.assertTrue(handler.stopMonitored.contains(newDir1));
                Assert.assertTrue(handler.stopMonitored.contains(newDir2));
                Assert.assertEquals(newFile, handler.deleted.get(newFile));
            }
            projCtx.reset();

            // Resources in java dir.
            // Add a new file
            Path targetDir = project.deploymentsDir.resolve("testjava1.jar");
            Path newResourceFile = Files.createFile(project.javaDir.resolve("foo2.properties"));
            Files.write(newResourceFile, "Hello".getBytes());
            handler = checkEvent(ctx, projCtx, false, false, false, false, false, new Checker() {
                @Override
                public boolean isOk(BootableAppEventHandler handler) {
                    return handler.copied.size() == 1;
                }
            });
            Assert.assertTrue(handler.redeploy);
            Assert.assertEquals(1, handler.copied.size());
            Path expectedTargetFile = targetDir.resolve(project.javaDir.relativize(newResourceFile));
            Assert.assertEquals(expectedTargetFile, handler.copied.get(newResourceFile));
            projCtx.reset();

            // Resources in resources dir.
            // Add a new file
            Path newResourceFile2 = Files.createFile(project.resourcesDir.resolve("foo3.properties"));
            Files.write(newResourceFile2, "Hello".getBytes());
            handler = checkEvent(ctx, projCtx, false, false, false, false, false, new Checker() {
                @Override
                public boolean isOk(BootableAppEventHandler handler) {
                    // In some very rare cases foo2.properties fire an additionnal change event.
                    // So size is not correct.
                    return handler.copied.containsKey(newResourceFile2);
                }
            });
            Assert.assertTrue(handler.redeploy);
            expectedTargetFile = targetDir.resolve(project.resourcesDir.relativize(newResourceFile2));
            Assert.assertEquals(handler.copied.toString(), expectedTargetFile, handler.copied.get(newResourceFile2));
            projCtx.reset();
        } finally {
            if (ctx != null) {
                ctx.cleanup();
            }
            project.cleanup();
        }
    }

    @Test
    public void testPomFile() throws Exception {
        Project project = new Project(false, false, null);
        DevWatchContext ctx = null;
        try {
            TestProjectContext projCtx = newWebProjectContext(project, "testweb3");

            ctx = new DevWatchContext(projCtx, watcher);
            // Update file
            String expected = "<!-- An empty pom file, updated by test-->";
            Files.write(project.pomFile, expected.getBytes());

            BootableAppEventHandler handler = checkEvent(ctx, projCtx, false, false, true, false, false, new Checker() {
                @Override
                public boolean isOk(BootableAppEventHandler handler) {
                    return handler.seenUpdated.size() == 1;
                }
            });
            Assert.assertTrue(handler.reset);
            Assert.assertFalse(handler.rebuildBootableJAR);
            Assert.assertTrue(handler.copied.isEmpty());
            Assert.assertEquals(1, handler.seenUpdated.size());
            Assert.assertTrue(handler.seenUpdated.contains(project.pomFile));
            projCtx.reset();

            // do a change that would imply a full rebuild
            projCtx.pluginConfigUpdated = true;
            // Update file
            expected = "<!-- An empty pom file, updated by test with change to the plugin config-->";
            Files.write(project.pomFile, expected.getBytes());
            handler = checkEvent(ctx, projCtx, false, false, true, false, false, new Checker() {
                @Override
                public boolean isOk(BootableAppEventHandler handler) {
                    return handler.seenUpdated.size() == 1;
                }
            });
            Assert.assertTrue(handler.rebuildBootableJAR);
            Assert.assertFalse(handler.reset);
            Assert.assertTrue(handler.copied.isEmpty());
            Assert.assertEquals(1, handler.seenUpdated.size());
            Assert.assertTrue(handler.seenUpdated.contains(project.pomFile));
            projCtx.reset();

        } finally {
            if (ctx != null) {
                ctx.cleanup();
            }
            project.cleanup();
        }
    }

    /**
     * ODO deletes the pom file on update.
     *
     * @throws Exception
     */
    @Test
    public void testPomFileODO() throws Exception {
        Project project = new Project(false, false, null);
        DevWatchContext ctx = null;
        try {
            TestProjectContext projCtx = newWebProjectContext(project, "testweb4");

            ctx = new DevWatchContext(projCtx, watcher);
            Files.delete(project.pomFile);
            Files.createFile(project.pomFile);
            // Update file
            String expected = "<!-- An empty pom file, updated by test-->";
            Files.write(project.pomFile, expected.getBytes());
            BootableAppEventHandler handler = checkEvent(ctx, projCtx, false, false, true, false, false, new Checker() {
                @Override
                public boolean isOk(BootableAppEventHandler handler) {
                    return handler.seenUpdated.size() == 1 && handler.deleted.size() == 1;
                }
            });
            Assert.assertTrue(handler.reset);
            Assert.assertFalse(handler.rebuildBootableJAR);
            Assert.assertTrue(handler.copied.isEmpty());
            Assert.assertEquals(1, handler.deleted.size());
            Assert.assertEquals(1, handler.seenUpdated.size());
            Assert.assertTrue(handler.seenUpdated.contains(project.pomFile));
            Assert.assertEquals(project.pomFile, handler.deleted.get(project.pomFile));
            projCtx.reset();

            // do a change that would imply a full rebuild
            projCtx.pluginConfigUpdated = true;
            Files.delete(project.pomFile);
            Files.createFile(project.pomFile);
            expected = "<!-- An empty pom file, updated by test with change to the plugin config-->";
            Files.write(project.pomFile, expected.getBytes());
            handler = checkEvent(ctx, projCtx, false, false, true, false, false, new Checker() {
                @Override
                public boolean isOk(BootableAppEventHandler handler) {
                    return handler.seenUpdated.size() == 1;
                }
            });
            Assert.assertTrue(handler.rebuildBootableJAR);
            Assert.assertFalse(handler.reset);
            Assert.assertTrue(handler.copied.isEmpty());
            Assert.assertEquals(1, handler.seenUpdated.size());
            Assert.assertTrue(handler.seenUpdated.contains(project.pomFile));
            projCtx.reset();

        } finally {
            if (ctx != null) {
                ctx.cleanup();
            }
            project.cleanup();
        }
    }

    private BootableAppEventHandler checkEvent(DevWatchContext ctx, TestProjectContext projCtx,
            boolean cleanup, boolean compile, boolean checkPluginConfig,
            boolean packageJar, boolean packageWar, Checker checker) throws Exception {

        boolean ok = false;
        BootableAppEventHandler handler = null;
        WatchKey key = null;
        long timeout = 60;
        do {
            key = watcher.poll();
            if (key != null) {
                if (handler == null) {
                    handler = ctx.newEventHandler();
                }
                for (WatchEvent<?> event : key.pollEvents()) {
                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> ev = (WatchEvent<Path>) event;
                    System.out.println("New event " + ev.kind() + " path " + ev.context());
                    Path absolutePath = ctx.getPath(key, ev.context());
                    if (absolutePath == null) {
                        continue;
                    }
                    handler.handle(ev.kind(), absolutePath);
                }
                key.reset();
            }
            if (handler != null) {
                ok = checker.isOk(handler);
            }
            if (!ok) {
                Thread.sleep(1000);
                timeout -= 1;
            }
        } while (timeout > 0 && !ok);
        handler.applyChanges();
        Assert.assertEquals(cleanup, projCtx.cleanupCalled);
        Assert.assertEquals(compile, projCtx.compileCalled);
        Assert.assertEquals(checkPluginConfig, projCtx.checkPluginCalled);
        Assert.assertEquals(packageWar, projCtx.packageWarCalled);
        Assert.assertEquals(packageJar, projCtx.packageJarCalled);
        return handler;
    }

    TestProjectContext newWebProjectContext(Project proj, String finalName) throws IOException {

        return new TestProjectContext(proj.baseDir, finalName, "war", proj.deploymentsDir, proj.buildDir, proj.javaDir, true, null, null, proj.resources, false);
    }

    TestProjectContext newJarProjectContext(Project proj, String finalName) throws IOException {

        return new TestProjectContext(proj.baseDir, finalName, "jar", proj.deploymentsDir, proj.buildDir, proj.javaDir, false, null, null, proj.resources, false);
    }
}
