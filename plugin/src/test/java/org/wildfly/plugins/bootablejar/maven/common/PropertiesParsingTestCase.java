/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2022 Red Hat, Inc., and individual contributors
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

package org.wildfly.plugins.bootablejar.maven.common;

import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Test basic properties manipulation when values are loaded from a file, i.e.: by using {@link Properties#load(Reader)}
 *
 * @author <a href="mailto:fburzigo@redhat.com">Fabio Burzigotti</a>
 */
public class PropertiesParsingTestCase {

    private static final Properties EXPECTED_WINDOWS_PATH_PROPS = new Properties();
    // See the "backslashes.properties" file in test/resources
    private static final String ESCAPED_DOUBLE_QUOTE = "escaped.double.quote";
    private static final String UNESCAPED_DOUBLE_QUOTE = "unescaped.double.quote";
    private static final String REGULAR_WINDOWS_PATH = "windows.path.regular";
    private static final String WINDOWS_PATH_WITH_INTERNAL_SPACE = "windows.path.internal.space";
    private static final String WINDOWS_PATH_WITH_UPPERCASE_VOLUME = "windows.path.uppercase.volume";
    private static final String WINDOWS_PATH_WITH_TRAILING_SLASH = "windows.path.trailing.slash";
    private static final String WINDOWS_PATH_UNESCAPED = "windows.path.unescaped";

    public PropertiesParsingTestCase() {
        // Double quotes don't need to be escaped, but when done the result would be the same, i.e. output the (").
        // See Properties.load() Javadoc
        EXPECTED_WINDOWS_PATH_PROPS.setProperty(ESCAPED_DOUBLE_QUOTE, "You say \\\\\"Yes\\\\\"");
        EXPECTED_WINDOWS_PATH_PROPS.setProperty(UNESCAPED_DOUBLE_QUOTE, "I say \"No\"");
        EXPECTED_WINDOWS_PATH_PROPS.setProperty(REGULAR_WINDOWS_PATH, "c:\\\\path\\\\in\\\\windows");
        EXPECTED_WINDOWS_PATH_PROPS.setProperty(WINDOWS_PATH_WITH_INTERNAL_SPACE, "c:\\\\path in\\\\windows");
        EXPECTED_WINDOWS_PATH_PROPS.setProperty(WINDOWS_PATH_WITH_UPPERCASE_VOLUME, "Z:\\\\path\\\\in\\\\windows");
        EXPECTED_WINDOWS_PATH_PROPS.setProperty(WINDOWS_PATH_WITH_TRAILING_SLASH, "b:\\\\path\\\\in\\\\windows\\\\");
        EXPECTED_WINDOWS_PATH_PROPS.setProperty(WINDOWS_PATH_UNESCAPED, "X:\\\\path\\\\in\\\\windows");
    }

    /**
     * Verifies that the {@link Utils#processProperties(Properties)} will properly escape Windows paths' double quotes
     * after such values are loaded from a {@code *.properties} file.
     *
     * @throws IOException When the {@code paths.properties} file is not found
     */
    @Test
    public void testProcessProperties() throws IOException {
        final String propertiesFile = "backslashes.properties";
        // arrange
        Properties rawProps = new Properties();
        try (InputStreamReader inputStreamReader = new InputStreamReader(
                Thread.currentThread().getContextClassLoader().getResourceAsStream(propertiesFile),
                StandardCharsets.UTF_8)) {
            rawProps.load(inputStreamReader);
        } catch (IOException e) {
            throw new IOException(
                    "Failed to load properties from " + propertiesFile + ": " + e.getLocalizedMessage());
        }
        // act
        Properties parsedProps = Utils.processProperties(rawProps);
        // assert
        parsedProps.entrySet().stream()
                .forEach(e -> Assert.assertEquals(e.getValue(), EXPECTED_WINDOWS_PATH_PROPS.get(e.getKey())));
    }
}
