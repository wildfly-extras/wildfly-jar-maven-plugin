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
import java.nio.file.Paths;
import java.util.List;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.wildfly.core.launcher.Launcher;

/**
 * Build and start a bootable JAR for dev mode. In order to be able to shutdown
 * the server started in 'dev' mode, the 'management' Galleon layer must have
 * been included. If that is not the case, the server would have to be killed.
 *
 * @author jfdenise
 */
@Mojo(name = "dev", requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME, defaultPhase = LifecyclePhase.COMPILE)
public final class DevBootableJarMojo extends AbstractDevBootableJarMojo {
    private static final String DEPLOYMENT_SCANNER_LAYER = "deployment-scanner";

    public static final String DEPLOYMENT_SCANNER_NAME = "wildfly-jar-for-dev-mode";
    /**
     * Indicates how {@code stdout} and {@code stderr} should be handled for the server process. A value of
     * {@code inherit} means that the standard output streams are inherited from the current process. Any other value is
     * assumed to be a path. In this case both {@code stdout} and {@code stderr} will be redirected to a file.
     */
    @Parameter(defaultValue = "${project.build.directory}/wildfly-jar-dev-stdout.log", property = "wildfly.bootable.stdout")
    public String stdout;

    @Override
    protected void doExecute() throws MojoExecutionException, MojoFailureException {
        try {
            boolean inherit = "inherit".equalsIgnoreCase(stdout);
            final Launcher launcher = Launcher.of(buildCommandBuilder(!inherit));

            if (inherit) {
                launcher.inherit();
            } else {
                final Path redirect = Paths.get(stdout);
                getLog().info(String.format("The stdout and stderr for the process are being logged to %s", redirect));
                launcher.setRedirectErrorStream(true)
                        .redirectOutput(redirect);
            }
            launcher.launch();
        } catch (Exception e) {
            throw new MojoExecutionException(e.getLocalizedMessage(), e);
        }
    }

    @Override
    protected void configureServer() {
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
    }

    @Override
    protected void configureCli(List<String> commands) {
        super.configureCli(commands);
        configureScanner(getDeploymentsDir(), commands);
    }

    private void configureScanner(Path deployments, List<String> commands) {
        String deploymentPath = deployments.toString().replace("\\", "\\\\");
        commands.add("if (outcome == success) of /subsystem=deployment-scanner/scanner=default:read-resource()");
        commands.add("/subsystem=deployment-scanner/scanner=default:remove()");
        commands.add("end-if");
        commands.add("/subsystem=deployment-scanner/scanner=" + DEPLOYMENT_SCANNER_NAME + ":add(scan-interval=1000,auto-deploy-exploded=false,"
                + "path=\"" + deploymentPath + "\")");
    }
}
