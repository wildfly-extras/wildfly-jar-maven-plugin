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

import org.wildfly.plugins.bootablejar.maven.cloud.CloudConfig;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.jboss.galleon.config.ConfigId;

/**
 * Build a bootable JAR containing application and provisioned server
 *
 * @author jfdenise
 */
@Mojo(name = "package", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.PACKAGE)
public class BuildBootableJarMojo extends AbstractBuildBootableJarMojo {

    /**
     * To enable cloud support. When cloud support is enabled, the created bootable JAR will operate properly in context such as openshift.
     * <br/>
     * In order to enable authenticated cluster jgroups protocol,
     * set &lt;enable-jgroups-password&gt;true&lt;/enable-jgroups-password&gt;. The environment variable JGROUPS_CLUSTER_PASSWORD
     * must then be set to the password value.
     */
    @Parameter(alias = "cloud")
    CloudConfig cloud;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().debug(String.format("Skipping run of %s:%s", project.getGroupId(), project.getArtifactId()));
            return;
        }
        if (!isPackageDev()) {
            if (cloud != null) {
                getLog().info("Cloud support is enabled");
                cloud.validate();
                for (String layer : cloud.getExtraLayers(this)) {
                    addExtraLayer(layer);
                }
            }
        }
        super.execute();
    }

    @Override
    protected void configureCli(List<String> commands) {
        if (cloud != null) {
            try {
                cloud.addCLICommands(this, commands);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    @Override
    protected ConfigId getDefaultConfig() {
        if(cloud == null) {
            return super.getDefaultConfig();
        } else {
            return new ConfigId("standalone", "standalone-microprofile-ha.xml");
        }
    }

    @Override
    protected void copyExtraContentInternal(Path wildflyDir, Path contentDir) throws Exception {
        if (cloud != null) {
           cloud.copyExtraContent(this, wildflyDir, contentDir);
        }
    }
}
