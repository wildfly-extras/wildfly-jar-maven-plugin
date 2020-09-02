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
package org.wildfly.plugins.bootablejar.maven.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.apache.maven.plugin.logging.Log;
import org.jboss.galleon.util.IoUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 *
 * @author jdenise
 */
public class LegacyPatchCleaner {

    private final Path wildflyDir;
    private final Set<Path> existingModules;
    private final Log log;
    private final Path overlays;
    private final Path modulesDir;
    private final Path overlaysFile;
    private final Path patchesDir;
    public LegacyPatchCleaner(Path wildflyDir, Log log) throws IOException {
        this.wildflyDir = wildflyDir;
        modulesDir = wildflyDir.resolve("modules").resolve("system").resolve("layers").resolve("base");
        overlays = modulesDir.resolve(".overlays");
        overlaysFile = overlays.resolve(".overlays");
        patchesDir = wildflyDir.resolve(".installation").resolve("patches");
        this.log = log;
        existingModules = captureModules();
    }

    private Log getLog() {
        return log;
    }

    private Set<Path> captureModules() throws IOException {
        Set<Path> existingModules = new HashSet<>();
        Files.walkFileTree(modulesDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path t, BasicFileAttributes bfa) throws IOException {
                if (t.getFileName().toString().equals("module.xml")) {
                    existingModules.add(modulesDir.relativize(t).getParent());
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return existingModules;
    }

    public void clean() throws Exception {
        cleanupModules();
    }

    private void cleanupModules() throws Exception {

        getLog().info("Legacy patch cleanup enabled, checking for unused resources...");

        if (Files.notExists(overlays)) {
            return;
        }

        if (Files.notExists(overlaysFile)) {
            return;
        }
        Map<String, Map<Path, Path>> newPatchedModules = new HashMap<>();
        Map<Path, Path> existingPatchedModules = new HashMap<>();
        Set<Path> overlayRoots = new HashSet<>();
        List<String> lines = Files.readAllLines(overlaysFile);
        for (String line : lines) {
            Path dir = overlays.resolve(line);
            overlayRoots.add(dir);
            if (!Files.exists(dir)) {
                continue;
            }
            Set<Path> inOverlay = new HashSet<>();
            Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path t, BasicFileAttributes bfa) throws IOException {
                    if (t.getFileName().toString().equals("module.xml")) {
                        inOverlay.add(dir.relativize(t).getParent());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            for (Path p : inOverlay) {
                if (!existingModules.contains(p)) {
                    Map<Path, Path> location = new HashMap<>();
                    location.put(p, dir.resolve(p).resolve("module.xml"));
                    newPatchedModules.put(p.getParent().toString().replace(File.separator, "."), location);
                } else {
                    existingPatchedModules.put(p, dir.resolve(p).resolve("module.xml"));
                }
            }
        }
        // rootReferences contains modules that can reference modules added by patching.
        // Patch introduced modules that are referenced from these roots can't be deleted.
        Map<Path, Path> rootReferences = new HashMap<>();
        rootReferences.putAll(existingPatchedModules);
        Set<String> newReferencedModules = null;
        do {
            newReferencedModules = getNewRequiredDependencies(rootReferences, newPatchedModules);
            rootReferences = new HashMap<>();
            for (String newlyReferenced : newReferencedModules) {
                // Remove the module that are now referenced from patched modules,
                // Patch introduced modules that are referenced become a root of references
                Map<Path, Path> loc = newPatchedModules.remove(newlyReferenced);
                rootReferences.putAll(loc);
            }
        } while (!rootReferences.isEmpty());
        // At the end we have newPatchedModules that contains not referenced modules.

        // Remove modules that we know are not referenced
        if (!newPatchedModules.isEmpty()) {
            getLog().info("Deleting module directories introduced by patch that are unused:");
            Set<Path> remainingModules = new HashSet<>();
            for (String module : newPatchedModules.keySet()) {
                Map<Path, Path> location = newPatchedModules.get(module);
                Path absoluteLocation = location.values().iterator().next().getParent();
                remainingModules.add(absoluteLocation);
                getLog().info(" * " + wildflyDir.relativize(absoluteLocation));
                IoUtils.recursiveDelete(absoluteLocation);
            }
            // Now that the modules have been deleted we must check if we can delete the parent directories
            for (Path p : remainingModules) {
                while (!overlayRoots.contains(p)) {
                    p = p.getParent();
                    String[] children = p.toFile().list();
                    if (children != null && children.length == 0) {
                        getLog().info(" * " + wildflyDir.relativize(p));
                        Files.delete(p);
                    } else {
                        break;
                    }
                }
            }
        }

        if (!existingPatchedModules.isEmpty()) {
            // Remove all patched modules original location
            getLog().info("Deleting patched module directories original locations:");
            for (Path p : existingPatchedModules.keySet()) {
                Path abs = modulesDir.resolve(p);
                getLog().info(" * " + wildflyDir.relativize(abs));
                IoUtils.recursiveDelete(abs);
            }
            // Now that the modules have been deleted we must check if we can delete the parent directories
            for (Path p : existingPatchedModules.keySet()) {
                Path abs = modulesDir.resolve(p);
                while (!abs.equals(modulesDir)) {
                    abs = abs.getParent();
                    String[] children = abs.toFile().list();
                    if (children != null && children.length == 0) {
                        getLog().info(" * " + wildflyDir.relativize(abs));
                        Files.delete(abs);
                    } else {
                        break;
                    }
                }
            }
        }
        //Delete overlay roots that are not used
        Set<Path> unusedLayers = new HashSet<>();
        Files.walkFileTree(overlays, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path t, BasicFileAttributes bfa) throws IOException {
                if (overlays.equals(t)) {
                    return FileVisitResult.CONTINUE;
                }
                if (!overlayRoots.contains(t)) {
                    unusedLayers.add(t);
                }
                return FileVisitResult.SKIP_SUBTREE;

            }
        });
        if (!unusedLayers.isEmpty()) {
            getLog().info("Deleting unused overlay directories:");
            for (Path unused : unusedLayers) {
                getLog().info(" * " + wildflyDir.relativize(unused));
                IoUtils.recursiveDelete(unused);
            }
        }

        if (Files.exists(patchesDir)) {
            getLog().info("Deleting " + wildflyDir.relativize(patchesDir) + " directory");
            IoUtils.recursiveDelete(patchesDir);
        }
    }

    private Set<String> getNewRequiredDependencies(Map<Path, Path> existingPatchedModules, Map<String, Map<Path, Path>> newModules) throws Exception {
        Set<String> dependencies = new HashSet<>();
        for (Path path : existingPatchedModules.values()) {
            FileInputStream fileInputStream = new FileInputStream(path.toFile());
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();

            Document document = documentBuilder.parse(fileInputStream);
            Element rootElement = document.getDocumentElement();
            XPathFactory factory = XPathFactory.newInstance();
            XPath xpath = factory.newXPath();
            if (!rootElement.getNodeName().equals("module")
                    && !rootElement.getNodeName().equals("module-alias")) {
                continue;
            }
            String moduleName = rootElement.getAttribute("name");
            NodeList lst = (NodeList) xpath.evaluate("/module/dependencies/module", rootElement, XPathConstants.NODESET);
            for (int i = 0; i < lst.getLength(); i++) {
                Element module = (Element) lst.item(i);
                String name = module.getAttribute("name");
                boolean optional = module.hasAttribute("optional") && "true".equals(module.getAttribute("optional"));
                if (newModules.containsKey(name) && !optional) {
                    getLog().info("New module " + name + " is a new dependency of " + moduleName + " patched module, will be not deleted.");
                    dependencies.add(name);
                }
            }
        }
        return dependencies;
    }

}
