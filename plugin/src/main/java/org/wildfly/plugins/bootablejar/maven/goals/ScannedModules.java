/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates
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
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.maven.plugin.MojoExecutionException;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.runtime.FeaturePackRuntime;
import org.jboss.galleon.runtime.PackageRuntime;
import org.jboss.galleon.runtime.ProvisioningRuntime;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

final class ScannedModules {

    private static final String MODULE_XML = "module.xml";
    private static final String PM = "pm";
    private static final String WILDFLY = "wildfly";
    private static final String MODULE = "module";
    private static final String MODULE_RUNTIME_KEY = "org.jboss.modules:jboss-modules";

    private final Map<String, Map<String, String>> perModule;
    private final String moduleRuntimeKey;
    private final String moduleRuntimeValue;

    ScannedModules(Map<String, Map<String, String>> perModule, String moduleRuntimeKey, String moduleRuntimeValue) {
        this.perModule = perModule;
        this.moduleRuntimeKey = moduleRuntimeKey;
        this.moduleRuntimeValue = moduleRuntimeValue;
    }

    Map<String, Map<String, String>> getPerModuleArtifacts() {
        return perModule;
    }

    String getModuleRuntime() {
        return moduleRuntimeValue;
    }

    Map<String, String> getProvisionedArtifacts() {
        Map<String, String> all = new HashMap<>();
        for (Map<String, String> artifacts : perModule.values()) {
            all.putAll(artifacts);
        }
        all.put(moduleRuntimeKey, moduleRuntimeValue);
        return all;
    }

    static ScannedModules scanProvisionedArtifacts(ProvisioningManager pm, ProvisioningConfig config)
            throws ProvisioningException, MojoExecutionException {
        Map<String, String> propsMap = new HashMap<>();
        Map<String, Map<String, String>> perModule = new TreeMap<>();
        try (ProvisioningRuntime rt = pm.getRuntime(config)) {
            for (FeaturePackRuntime fprt : rt.getFeaturePacks()) {
                Path artifactProps = fprt.getResource(AbstractBuildBootableJarMojo.WILDFLY_ARTIFACT_VERSIONS_RESOURCE_PATH);
                try {
                    AbstractBuildBootableJarMojo.readProperties(artifactProps, propsMap);
                } catch (Exception ex) {
                    throw new MojoExecutionException("Error reading artifact versions", ex);
                }
            }
            for (FeaturePackRuntime fprt : rt.getFeaturePacks()) {
                processPackages(fprt, perModule, propsMap);
            }
        }

        String moduleRuntimeValue = propsMap.get(MODULE_RUNTIME_KEY);
        if (moduleRuntimeValue == null) {
            throw new ProvisioningException("No JBoss Modules runtime found");
        }
        return new ScannedModules(perModule, MODULE_RUNTIME_KEY, moduleRuntimeValue);
    }

    private static void processPackages(final FeaturePackRuntime fp,
            Map<String, Map<String, String>> perModule, Map<String, String> propsMap) throws ProvisioningException {
        Map<Path, PackageRuntime> jbossModules = new HashMap<>();
        for (PackageRuntime pkg : fp.getPackages()) {
            final Path pmWfDir = pkg.getResource(PM, WILDFLY);
            if (!Files.exists(pmWfDir)) {
                continue;
            }
            final Path moduleDir = pmWfDir.resolve(MODULE);
            if (Files.exists(moduleDir)) {
                processModules(pkg, moduleDir, jbossModules);
            }
        }
        for (Map.Entry<Path, PackageRuntime> entry : jbossModules.entrySet()) {
            final PackageRuntime pkg = entry.getValue();
            try {
                processModuleTemplate(pkg, entry.getKey(), perModule, propsMap);
            } catch (IOException | ParserConfigurationException | ProvisioningException | SAXException e) {
                throw new ProvisioningException("Failed to process JBoss module XML template for feature-pack "
                        + pkg.getFeaturePackRuntime().getFPID() + " package " + pkg.getName(), e);
            }
        }
    }

    private static void processModules(PackageRuntime pkg, Path fpModuleDir,
            Map<Path, PackageRuntime> jbossModules) throws ProvisioningException {
        try {
            Files.walkFileTree(fpModuleDir, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    if (file.getFileName().toString().equals(MODULE_XML)) {
                        jbossModules.put(fpModuleDir.relativize(file), pkg);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new ProvisioningException("Failed to process modules from package " + pkg.getName()
                    + " from feature-pack " + pkg.getFeaturePackRuntime().getFPID(), e);
        }
    }

    private static void processModuleTemplate(PackageRuntime pkg, Path moduleXmlRelativePath,
            Map<String, Map<String, String>> perModule, Map<String, String> propsMap) throws ProvisioningException, IOException, ParserConfigurationException, SAXException {
        final Path moduleTemplate = pkg.getResource(PM, WILDFLY, MODULE).resolve(moduleXmlRelativePath);

        try (InputStream reader = Files.newInputStream(moduleTemplate)) {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();

            Document document = documentBuilder.parse(reader);
            Element root = document.getDocumentElement();
            NodeList lst = root.getChildNodes();
            for (int i = 0; i < lst.getLength(); i++) {
                Node n = lst.item(i);
                if (n instanceof Element) {
                    if ("resources".equals(n.getNodeName())) {
                        Element resources = (Element) n;
                        NodeList artifacts = resources.getChildNodes();
                        for (int j = 0; j < artifacts.getLength(); j++) {
                            Node a = artifacts.item(j);
                            if (a instanceof Element) {
                                if ("artifact".equals(a.getNodeName())) {
                                    String name = ((Element) a).getAttribute("name");
                                    if (name.startsWith("${")) {
                                        name = name.substring(2, name.length() - 1);
                                        Map<String, String> m = perModule.get(pkg.getName());
                                        if (m == null) {
                                            m = new TreeMap<>();
                                            perModule.put(pkg.getName(), m);
                                        }
                                        String value = propsMap.get(name);
                                        m.put(name, value);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
