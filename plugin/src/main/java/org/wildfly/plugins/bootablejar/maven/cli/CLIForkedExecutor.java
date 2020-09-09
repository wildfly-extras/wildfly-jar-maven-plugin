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

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Execute CLI in forked process entry point.
 *
 * @author jdenise
 */
public class CLIForkedExecutor {

    public static void main(String[] args) throws Exception {
        Path jbossHome = Paths.get(args[0]);
        Path cliOutput = Paths.get(args[1]);
        Path systemProperties = Paths.get(args[2]);
        Path script = Paths.get(args[3]);
        Boolean resolveExpression = Boolean.parseBoolean(args[4]);
        Properties properties = new Properties();
        try (FileInputStream in = new FileInputStream(systemProperties.toFile())) {
            properties.load(in);
            for (String key : properties.stringPropertyNames()) {
                System.setProperty(key, properties.getProperty(key));
            }
        }
        try (CLIWrapper executor = new CLIWrapper(jbossHome, resolveExpression, CLIForkedExecutor.class.getClassLoader())) {
            try {
                for (String command : Files.readAllLines(script)) {
                    executor.handle(command);
                }
            } finally {
                Files.write(cliOutput, executor.getOutput().getBytes(StandardCharsets.UTF_8));
            }
        }
    }
}
