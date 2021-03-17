package org.wildfly.plugins.bootablejar.maven.cli;

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
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.logging.Log;
import org.jboss.galleon.Errors;

import org.jboss.galleon.ProvisioningException;


/**
 *
 * @author jdenise
 */
public class ForkedCLIUtil {

    private static String javaHome;
    private static String javaCmd;

    private static String getJavaHome() {
        return javaHome == null ? javaHome = System.getProperty("java.home") : javaHome;
    }

    private static String getJavaCmd() {
        return javaCmd == null ? javaCmd = Paths.get(getJavaHome()).resolve("bin").resolve("java").toString() : javaCmd;
    }

    public static void fork(Log log, String[] artifacts, Class<?> clazz, Path home, Path output, String... args) throws Exception {
        // prepare the classpath
        final StringBuilder cp = new StringBuilder();
        for (String loc : artifacts) {
            cp.append(loc).append(File.pathSeparator);
        }
        collectCpUrls(getJavaHome(), Thread.currentThread().getContextClassLoader(), cp);

        Path properties = storeSystemProps();

        final List<String> argsList = new ArrayList<>();
        argsList.add(getJavaCmd());
        argsList.add("-server");
        argsList.add("-cp");
        argsList.add(cp.toString());
        argsList.add(clazz.getName());
        argsList.add(home.toString());
        argsList.add(output.toString());
        argsList.add(properties.toString());
        for (String s : args) {
            argsList.add(s);
        }

        try {
            final Process p;
            try {
                p = new ProcessBuilder(argsList).redirectErrorStream(true).start();
            } catch (IOException e) {
                throw new ProvisioningException("Failed to start forked process", e);
            }
            StringBuilder traces = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                while (line != null) {
                    traces.append(line).append(System.lineSeparator());
                    line = reader.readLine();
                }
                if (p.isAlive()) {
                    try {
                        p.waitFor();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            int exitCode = p.exitValue();
            if (exitCode != 0) {
                log.error("Error executing CLI:" + traces);
                throw new Exception("CLI execution failed.");
            }
        } finally {
            Files.deleteIfExists(properties);
        }
    }

    private static Path storeSystemProps() throws ProvisioningException {
        final Path props;
        try {
            props = Files.createTempFile("wfbootablejar", "sysprops");
        } catch (IOException e) {
            throw new ProvisioningException("Failed to create a tmp file", e);
        }
        try (BufferedWriter writer = Files.newBufferedWriter(props)) {
            System.getProperties().store(writer, "");
        } catch (IOException e) {
            throw new ProvisioningException(Errors.writeFile(props), e);
        }
        return props;
    }

    private static void collectCpUrls(String javaHome, ClassLoader cl, StringBuilder buf) throws URISyntaxException {
        final ClassLoader parentCl = cl.getParent();
        if(parentCl != null) {
            collectCpUrls(javaHome, cl.getParent(), buf);
        }
        if (cl instanceof URLClassLoader) {
            for (URL url : ((URLClassLoader)cl).getURLs()) {
                final String file = new File(url.toURI()).getAbsolutePath();
                if(file.startsWith(javaHome)) {
                    continue;
                }
                if (buf.length() > 0) {
                    buf.append(File.pathSeparatorChar);
                }
                buf.append(file);
            }
        }
    }
}
