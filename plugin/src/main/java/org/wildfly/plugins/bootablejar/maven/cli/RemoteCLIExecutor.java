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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import org.wildfly.plugins.bootablejar.maven.goals.AbstractBuildBootableJarMojo;

/**
 * A CLI executor, that forks CLI execution in a remote process.
 *
 * @author jdenise
 */
public class RemoteCLIExecutor implements CLIExecutor {

    private final Level level;
    private final AbstractBuildBootableJarMojo mojo;
    private final Path output;
    private final Path jbossHome;
    private final String[] cp;
    private final boolean resolveExpression;

    public RemoteCLIExecutor(Path jbossHome, List<Path> cliArtifacts,
            AbstractBuildBootableJarMojo mojo, boolean resolveExpression) throws Exception {
        this.jbossHome = jbossHome;
        this.mojo = mojo;
        this.resolveExpression = resolveExpression;
        level = mojo.disableLog();
        output = File.createTempFile("cli-script-output", null).toPath();
        Files.deleteIfExists(output);
        cp = new String[cliArtifacts.size()];
        int i = 0;
        for (Path p : cliArtifacts) {
            cp[i] = p.toString();
            i += 1;
        }
    }

    @Override
    public void handle(String command) throws Exception {
        throw new UnsupportedOperationException("handle is unsupported, call execute instead.");
    }

    @Override
    public String getOutput() throws Exception {
        StringBuilder out = new StringBuilder();
        for (String s : Files.readAllLines(output)) {
            out.append(s).append("\n");
        }
        return out.toString();
    }

    @Override
    public void close() throws Exception {
        try {
            Files.deleteIfExists(output);
        } finally {
            mojo.enableLog(level);
        }
    }

    @Override
    public void execute(List<String> commands) throws Exception {
        Path script = File.createTempFile("cli-script", null).toPath();
        Files.deleteIfExists(script);
        Files.deleteIfExists(output);
        StringBuilder cmds = new StringBuilder();
        for (String cmd : commands) {
            cmds.append(cmd).append(System.lineSeparator());
        }
        Files.write(script, cmds.toString().getBytes(StandardCharsets.UTF_8));
        String[] args = new String[2];
        args[0] = script.toString();
        args[1] = Boolean.toString(resolveExpression);
        try {
            ForkedCLIUtil.fork(mojo.getLog(), cp, CLIForkedExecutor.class, jbossHome, output, args);
        } finally {
            Files.deleteIfExists(script);
        }
    }

    @Override
    public void generateBootLoggingConfig() throws Exception {
        ForkedCLIUtil.fork(mojo.getLog(), cp, CLIForkedBootConfigGenerator.class, jbossHome, output);
    }
}
