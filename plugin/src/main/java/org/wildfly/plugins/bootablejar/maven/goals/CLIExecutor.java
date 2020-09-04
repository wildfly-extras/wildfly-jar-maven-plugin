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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Set;
import java.util.logging.Level;
import org.apache.maven.artifact.Artifact;
import org.jboss.as.controller.client.ModelControllerClient;

/**
 * A CLI executor, resolving CLI classes from an URL Classloader. We can't have
 * cli/embedded/jboss modules in plugin classpath, it causes issue because we
 * are sharing the same jboss module classes between execution run inside the
 * same JVM.
 *
 * CLI dependencies are retrieved from provisioned server artifacts list and
 * resolved using maven. In addition jboss-modules.jar located in the
 * provisioned server * is added.
 *
 * @author jdenise
 */
class CLIExecutor implements AutoCloseable {

    private final Level level;
    private final Object ctx;
    private final Method handle;
    private final Method terminateSession;
    private final Method getModelControllerClient;
    private final ClassLoader originalCl;
    private final URLClassLoader cliCl;
    private final String origConfig;
    private final AbstractBuildBootableJarMojo mojo;
    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    CLIExecutor(Path jbossHome, Set<Artifact> cliArtifacts,
            AbstractBuildBootableJarMojo mojo, boolean resolveExpression) throws Exception {
        this.mojo = mojo;
        level = mojo.disableLog();
        Path config = jbossHome.resolve("bin").resolve("jboss-cli.xml");
        origConfig = System.getProperty("jboss.cli.config");
        if (Files.exists(config)) {
            System.setProperty("jboss.cli.config", config.toString());
        }


        final URL[] cp = new URL[cliArtifacts.size() + 1];
        cp[0] = jbossHome.resolve("jboss-modules.jar").toUri().toURL();
        mojo.getLog().debug("CLI artifacts " + cliArtifacts);
        Iterator<Artifact> it = cliArtifacts.iterator();
        int i = 1;
        while (it.hasNext()) {
            cp[i] = mojo.resolveArtifact(it.next()).toUri().toURL();
            i += 1;
        }
        originalCl = Thread.currentThread().getContextClassLoader();
        cliCl = new URLClassLoader(cp, originalCl);
        Thread.currentThread().setContextClassLoader(cliCl);
        final Object builder = cliCl.loadClass("org.jboss.as.cli.impl.CommandContextConfiguration$Builder").newInstance();
        final Method setEchoCommand = builder.getClass().getMethod("setEchoCommand", boolean.class);
        setEchoCommand.invoke(builder, true);
        final Method setResolve = builder.getClass().getMethod("setResolveParameterValues", boolean.class);
        setResolve.invoke(builder, resolveExpression);
        final Method setOutput = builder.getClass().getMethod("setConsoleOutput", OutputStream.class);
        setOutput.invoke(builder, out);
        Object ctxConfig = builder.getClass().getMethod("build").invoke(builder);
        Object factory = cliCl.loadClass("org.jboss.as.cli.CommandContextFactory").getMethod("getInstance").invoke(null);
        final Class<?> configClass = cliCl.loadClass("org.jboss.as.cli.impl.CommandContextConfiguration");
        ctx = factory.getClass().getMethod("newCommandContext", configClass).invoke(factory, ctxConfig);
        handle = ctx.getClass().getMethod("handle", String.class);
        terminateSession = ctx.getClass().getMethod("terminateSession");
        getModelControllerClient = ctx.getClass().getMethod("getModelControllerClient");
    }

    void handle(String command) throws Exception {
        handle.invoke(ctx, command);
    }

    String getOutput() {
        return out.toString();
    }

    @Override
    public void close() throws Exception {
        try {
            terminateSession.invoke(ctx);
        } finally {
            Thread.currentThread().setContextClassLoader(originalCl);
            try {
                cliCl.close();
            } catch (IOException e) {
            }
            mojo.enableLog(level);
            if (origConfig != null) {
                System.setProperty("jboss.cli.config", origConfig);
            }
        }
    }

    ModelControllerClient getModelControllerClient() throws Exception {
        return (ModelControllerClient) getModelControllerClient.invoke(ctx);
    }
}
