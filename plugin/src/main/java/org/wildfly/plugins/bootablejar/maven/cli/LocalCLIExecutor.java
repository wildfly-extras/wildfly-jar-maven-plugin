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

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import org.wildfly.plugins.bootablejar.maven.goals.AbstractBuildBootableJarMojo;
import org.wildfly.plugins.bootablejar.maven.goals.BootLoggingConfiguration;

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
public class LocalCLIExecutor implements CLIExecutor {

    private final Level level;
    private final ClassLoader originalCl;
    private final URLClassLoader cliCl;
    private final AbstractBuildBootableJarMojo mojo;
    private final CLIWrapper cliWrapper;

    public LocalCLIExecutor(Path jbossHome, List<Path> cliArtifacts,
            AbstractBuildBootableJarMojo mojo, boolean resolveExpression, BootLoggingConfiguration bootLoggingConfiguration) throws Exception {
        this.mojo = mojo;
        level = mojo.disableLog();
        final URL[] cp = new URL[cliArtifacts.size()];
        Iterator<Path> it = cliArtifacts.iterator();
        int i = 0;
        while (it.hasNext()) {
            cp[i] = it.next().toUri().toURL();
            i += 1;
        }
        originalCl = Thread.currentThread().getContextClassLoader();
        cliCl = new URLClassLoader(cp, originalCl);
        Thread.currentThread().setContextClassLoader(cliCl);
        cliWrapper = new CLIWrapper(jbossHome, resolveExpression, cliCl, bootLoggingConfiguration);
    }

    @Override
    public void handle(String command) throws Exception {
        cliWrapper.handle(command);
    }

    @Override
    public String getOutput() {
        return cliWrapper.getOutput();
    }

    @Override
    public void close() throws Exception {
        try {
            cliWrapper.close();
        } finally {
            Thread.currentThread().setContextClassLoader(originalCl);
            try {
                cliCl.close();
            } catch (IOException e) {
            }
            mojo.enableLog(level);
        }
    }

    @Override
    public void execute(List<String> commands) throws Exception {
        for (String cmd : commands) {
            handle(cmd);
        }
    }

    @Override
    public void generateBootLoggingConfig() throws Exception {
        cliWrapper.generateBootLoggingConfig();
    }
}
