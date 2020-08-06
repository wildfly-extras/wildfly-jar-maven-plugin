/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2020 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.plugins.bootablejar.maven.goals;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class TestEnvironment {
    private static final int TIMEOUT = getProperty("ts.timeout", 60);
    private static final String HOST = System.getProperty("ts.hostname", "127.0.0.1");
    private static final int HTTP_PORT = getProperty("ts.http.port", 8080);
    private static final int MGMT_PORT = getProperty("ts.mgmt.port", 9990);
    private static final String TMP_DIR = System.getProperty("java.io.tmpdir", "target");
    private static final boolean IS_WINDOWS;

    static {
        final String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        IS_WINDOWS = os.contains("win");
    }

    /**
     * Gets the timeout in seconds.
     * <p>
     * The default is 60 seconds and can be overridden via the {@code ts.timeout} system property.
     * </p>
     *
     * @return the timeout in seconds
     */
    static int getTimeout() {
        return TIMEOUT;
    }

    /**
     * Gets the hostname.
     * <p>
     * The default is 127.0.0.1 and can be overridden via the {@code ts.hostname} system property.
     * </p>
     *
     * @return the hostname
     */
    static String getHost() {
        return HOST;
    }

    /**
     * Gets the HTTP port
     * <p>
     * The default is 8080 and can be overridden via the {@code ts.http.port} system property.
     * </p>
     *
     * @return the HTTP port
     */
    static int getHttpPort() {
        return HTTP_PORT;
    }

    /**
     * Gets the management port
     * <p>
     * The default is 9990 and can be overridden via the {@code ts.mgmt.port} system property.
     * </p>
     *
     * @return the management port
     */
    static int getManagementPort() {
        return MGMT_PORT;
    }

    /**
     * Checks if the running environment is Windows.
     *
     * @return {@code true} if the running environment is Windows, otherwise {@code false}
     */
    static boolean isWindows() {
        return IS_WINDOWS;
    }

    /**
     * Creates a temporary path based on the {@code java.io.tmpdir} system property.
     *
     * @param paths the additional portions of the path
     *
     * @return the path
     */
    static Path createTempPath(final String... paths) {
        return Paths.get(TMP_DIR, paths);
    }

    private static int getProperty(final String name, final int dft) {
        final String value = System.getProperty(name);
        return value == null ? dft : Integer.parseInt(value);
    }
}
