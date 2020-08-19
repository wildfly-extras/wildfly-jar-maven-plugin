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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Build and start a bootable jar for dev mode
 *
 * @author jfdenise
 */
@Mojo(name = "dev", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.COMPILE)
public final class DevBootableJarMojo extends AbstractBuildBootableJarMojo {
    private static final String DEPLOYMENT_SCANNER_LAYER = "deployment-scanner";

    public static final String DEPLOYMENT_SCANNER_NAME = "wildfly-jar-for-dev-mode";

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().debug(String.format("Skipping run of %s:%s", project.getGroupId(), project.getArtifactId()));
            return;
        }
        if (Files.exists(getProvisioningFile()) && !hasLayers()) {
            getLog().warn("Dev mode, can't enforce provisioning of " + DEPLOYMENT_SCANNER_LAYER
                    + ". Make sure your provisioned configuration contains deployment-scanner subsystem for dev mode to properly operate.");
        } else {
            if (getExcludedLayers().contains(DEPLOYMENT_SCANNER_LAYER)) {
                getLog().warn("Dev mode, removing layer " + DEPLOYMENT_SCANNER_LAYER + " from the list of excluded layers to ensure dev mode can be operated");
                getExcludedLayers().remove(DEPLOYMENT_SCANNER_LAYER);
            }
            getLog().info("Dev mode, adding layer " + DEPLOYMENT_SCANNER_LAYER + " to ensure dev mode can be operated");
            addExtraLayer(DEPLOYMENT_SCANNER_LAYER);
        }
        hollowJar = true;
        super.execute();
        new StartBootableJarMojo().startDevMode(project);
    }

    @Override
    protected void configureCli(List<String> commands) {
        configureScanner(getDeploymentsDir(), commands);
    }

    private void configureScanner(Path deployments, List<String> commands) {
        String deploymentPath = deployments.toString().replace("\\", "\\\\");
        commands.add("/subsystem=deployment-scanner/scanner=" + DEPLOYMENT_SCANNER_NAME + ":add(scan-interval=1000,auto-deploy-exploded=false,"
                + "path=\"" + deploymentPath + "\")");
    }
}
