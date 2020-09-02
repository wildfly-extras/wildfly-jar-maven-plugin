package org.wildfly.plugins.bootablejar.patching;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.jboss.as.patching.IoUtils;
import org.jboss.as.patching.metadata.ModuleItem;

/**
 * @author Martin Simka
 */
public class Module {
    private String namespace;
    private String name;
    private String slot;
    private Properties properties;
    private List<String> dependencies;
    private List<ResourceItem> resourceRoots;
    private List<ResourceItem> miscFiles;
    private Path originalModuleXml;

    private Module(Builder builder) {
        this.namespace = builder.namespace;
        this.name = builder.name;
        this.slot = builder.slot;
        this.properties = builder.properties;
        this.dependencies = builder.dependencies;
        this.resourceRoots = builder.resourceRoots;
        this.miscFiles = builder.miscFiles;
        this.originalModuleXml = builder.originalModuleXml;
    }

    public String getName() {
        return name;
    }

    public String getSlot() {
        return slot;
    }

    public String generateXml() throws IOException {
        StringBuilder stringBuilder = new StringBuilder();
        if (originalModuleXml != null) {
            String content = new String(Files.readAllBytes(originalModuleXml));
            if (!properties.isEmpty()) {
                StringBuilder props = new StringBuilder();
                String propertyTemplate = "          <property name=\"%s\" value=\"%s\"/>\n";
                for (String key : properties.stringPropertyNames()) {
                    props.append(String.format(propertyTemplate, key, properties.getProperty(key)));
                }
                content = content.replace("<properties>", "<properties>\n" + props.toString());
            }
            if (!resourceRoots.isEmpty()) {
                StringBuilder resources = new StringBuilder();
                String resourceRootTemplate = "          <resource-root path=\"%s\"/>\n";
                for (ResourceItem resourceRoot : resourceRoots) {
                    resources.append(String.format(resourceRootTemplate, resourceRoot.getItemName()));
                }
                content = content.replace("<resources>", "<resources>\n" + resources.toString());
            }
            if (!dependencies.isEmpty()) {
                StringBuilder deps = new StringBuilder();
                String dependencyTemplate = "          <module name=\"%s\"/>\n";
                for (String module : dependencies) {
                    deps.append(String.format(dependencyTemplate, module));
                }
                content = content.replace("<dependencies>", "<dependencies>\n" + deps.toString());
            }
            stringBuilder.append(content);
        } else {
            stringBuilder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            String rootElementTemplate = "<module xmlns=\"%s\" name=\"%s\" slot=\"%s\">\n";
            stringBuilder.append(String.format(rootElementTemplate, namespace, name, slot));
            if (!properties.isEmpty()) {
                stringBuilder.append("     <properties>\n");
                String propertyTemplate = "          <property name=\"%s\" value=\"%s\"/>\n";
                for (String key : properties.stringPropertyNames()) {
                    stringBuilder.append(String.format(propertyTemplate, key, properties.getProperty(key)));
                }
                stringBuilder.append("     </properties>\n");
            }

            stringBuilder.append("     <resources>\n");
            String resourceRootTemplate = "          <resource-root path=\"%s\"/>\n";
            for (ResourceItem resourceRoot : resourceRoots) {
                stringBuilder.append(String.format(resourceRootTemplate, resourceRoot.getItemName()));
            }
            stringBuilder.append(String.format(resourceRootTemplate, "."));
            stringBuilder.append("     </resources>\n");

            if (!dependencies.isEmpty()) {
                stringBuilder.append("     <dependencies>\n");
                String dependencyTemplate = "          <module name=\"%s\"/>\n";
                for (String module : dependencies) {
                    stringBuilder.append(String.format(dependencyTemplate, module));
                }
                stringBuilder.append("     </dependencies>\n");
            }
            stringBuilder.append("</module>\n");
        }
        return stringBuilder.toString();
    }

    /**
     * writes module to disk
     *
     * @param baseDir usually modules dir, written path starts with first part of module name
     * @return main dir
     * @throws java.io.IOException
     */
    public File writeToDisk(File baseDir) throws IOException {
        File mainDir = IoUtils.mkdir(baseDir, (name + "." + slot).split("\\."));
        File moduleXml = PatchingTestUtil.touch(mainDir, "module.xml");
        PatchingTestUtil.dump(moduleXml, generateXml().getBytes(StandardCharsets.UTF_8));
        for (ResourceItem resourceRoot : resourceRoots) {
            File f = PatchingTestUtil.touch(mainDir, resourceRoot.getItemName());
            PatchingTestUtil.dump(f, resourceRoot.getContent());
        }
        for (ResourceItem miscFile : miscFiles) {
            File f = PatchingTestUtil.touch(mainDir, miscFile.getItemName());
            PatchingTestUtil.dump(f, miscFile.getContent());
        }
        if (originalModuleXml != null) {
            // copy all existing resources
            Path parent = originalModuleXml.getParent();
            for (File f : parent.toFile().listFiles()) {
                if (!f.getName().equals("module.xml")) {
                    Files.copy(f.toPath(), mainDir.toPath().resolve(f.getName()));
                }
            }
        }
        return mainDir;
    }


    public static class Builder {
        private String namespace;
        private String name;
        private String slot;
        private Properties properties;
        private List<String> dependencies;
        private List<ResourceItem> resourceRoots;
        private List<ResourceItem> miscFiles;
        private Path originalModuleXml;

        public Builder(String name, String namespace) {
            if (name == null) {
                throw new NullPointerException("name");
            }
            if (namespace == null) {
                throw new NullPointerException("namespace");
            }
            this.name = name;
            this.namespace = namespace;
            properties = new Properties();
            dependencies = new ArrayList<String>();
            resourceRoots = new ArrayList<ResourceItem>();
            miscFiles = new ArrayList<ResourceItem>();
        }

        public Builder(String name) {
            if (name == null) {
                throw new NullPointerException("name");
            }
            this.name = name;
            this.namespace = "urn:jboss:module:1.2";
            properties = new Properties();
            dependencies = new ArrayList<String>();
            resourceRoots = new ArrayList<ResourceItem>();
            miscFiles = new ArrayList<ResourceItem>();
        }

        public Builder slot(String slot) {
            if (slot == null) {
                throw new NullPointerException("slot");
            }
            this.slot = slot;
            return this;
        }

        public Builder property(String name, String value) {
            if (name == null) {
                throw new NullPointerException("name");
            }
            if (value == null) {
                throw new NullPointerException("value");
            }
            properties.setProperty(name, value);
            return this;
        }

        public Builder dependency(String moduleName) {
            if (moduleName == null) {
                throw new NullPointerException("moduleName");
            }
            dependencies.add(moduleName);
            return this;
        }

        public Builder resourceRoot(ResourceItem resourceRoot) {
            if (resourceRoot == null) {
                throw new NullPointerException("resourceRoot");
            }
            resourceRoots.add(resourceRoot);
            return this;
        }

        public Builder miscFile(ResourceItem miscFile) {
            if (miscFile == null) {
                throw new NullPointerException("miscFile");
            }
            miscFiles.add(miscFile);
            return this;
        }

        public Builder originalModuleXml(Path path) {
            if (path == null) {
                throw new NullPointerException("path");
            }
            if (!Files.exists(path)) {
                throw new RuntimeException("Path doesn't exist " + path);
            }
            originalModuleXml = path;
            return this;
        }

        public Module build() {
            assert notNull(name);
            assert notNull(namespace);
            if (slot == null) {
                slot = ModuleItem.MAIN_SLOT;
            }
            return new Module(this);
        }

        static boolean notNull(Object o) {
            return o != null;
        }
    }
}
