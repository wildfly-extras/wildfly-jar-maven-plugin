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
package org.wildfly.plugins.bootablejar.maven.cli;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.jboss.as.controller.client.ModelControllerClient;
import org.wildfly.plugins.bootablejar.maven.goals.BootLoggingConfiguration;

/**
 * A CLI executor, resolving CLI classes from the provided Classloader. We can't
 * have cli/embedded/jboss modules in plugin classpath, it causes issue because
 * we are sharing the same jboss module classes between execution run inside the
 * same JVM.
 *
 * CLI dependencies are retrieved from provisioned server artifacts list and
 * resolved using maven. In addition jboss-modules.jar located in the
 * provisioned server is added.
 *
 * @author jdenise
 */
public class CLIWrapper implements AutoCloseable {

    private final Object ctx;
    private final Method handle;
    private final Method terminateSession;
    private final Method getModelControllerClient;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final String origConfig;
    private final Path jbossHome;
    private final BootLoggingConfiguration bootLoggingConfiguration;

    public CLIWrapper(Path jbossHome, boolean resolveExpression, ClassLoader loader) throws Exception {
        this(jbossHome, resolveExpression, loader, null);
    }

    public CLIWrapper(Path jbossHome, boolean resolveExpression, ClassLoader loader, BootLoggingConfiguration bootLoggingConfiguration) throws Exception {
        this.jbossHome = jbossHome;
        Path config = jbossHome.resolve("bin").resolve("jboss-cli.xml");
        origConfig = System.getProperty("jboss.cli.config");
        if (Files.exists(config)) {
            System.setProperty("jboss.cli.config", config.toString());
        }


        final Object builder = loader.loadClass("org.jboss.as.cli.impl.CommandContextConfiguration$Builder").newInstance();
        final Method setEchoCommand = builder.getClass().getMethod("setEchoCommand", boolean.class);
        setEchoCommand.invoke(builder, true);
        final Method setResolve = builder.getClass().getMethod("setResolveParameterValues", boolean.class);
        setResolve.invoke(builder, resolveExpression);
        final Method setOutput = builder.getClass().getMethod("setConsoleOutput", OutputStream.class);
        setOutput.invoke(builder, out);
        Object ctxConfig = builder.getClass().getMethod("build").invoke(builder);
        Object factory = loader.loadClass("org.jboss.as.cli.CommandContextFactory").getMethod("getInstance").invoke(null);
        final Class<?> configClass = loader.loadClass("org.jboss.as.cli.impl.CommandContextConfiguration");
        ctx = factory.getClass().getMethod("newCommandContext", configClass).invoke(factory, ctxConfig);
        handle = ctx.getClass().getMethod("handle", String.class);
        terminateSession = ctx.getClass().getMethod("terminateSession");
        getModelControllerClient = ctx.getClass().getMethod("getModelControllerClient");
        this.bootLoggingConfiguration = bootLoggingConfiguration;
    }

    public void handle(String command) throws Exception {
        handle.invoke(ctx, command);
    }

    public String getOutput() {
        return out.toString();
    }

    @Override
    public void close() throws Exception {
        try {
            terminateSession.invoke(ctx);
        } finally {
            if (origConfig != null) {
                System.setProperty("jboss.cli.config", origConfig);
            }
        }
    }

    private ModelControllerClient getModelControllerClient() throws Exception {
        return (ModelControllerClient) getModelControllerClient.invoke(ctx);
    }

    public void generateBootLoggingConfig() throws Exception {
        Objects.requireNonNull(bootLoggingConfiguration);
        Exception toThrow = null;
        try {
            // Start the embedded server
            handle("embed-server --jboss-home=" + jbossHome + " --std-out=discard");
            // Get the client used to execute the management operations
            final ModelControllerClient client = getModelControllerClient();
            // Update the bootable logging config
            final Path configDir = jbossHome.resolve("standalone").resolve("configuration");
            bootLoggingConfiguration.generate(configDir, client);
        } catch (Exception e) {
            toThrow = e;
        } finally {
            try {
                // Always stop the embedded server
                handle("stop-embedded-server");
            } catch (Exception e) {
                if (toThrow != null) {
                    e.addSuppressed(toThrow);
                }
                toThrow = e;
            }
        }
        // Check if an error has been thrown and throw it.
        if (toThrow != null) {
            throw toThrow;
        }
    }
}
