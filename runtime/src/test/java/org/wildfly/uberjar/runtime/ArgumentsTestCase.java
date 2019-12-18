/*
 * Copyright 2016-2019 Red Hat, Inc. and/or its affiliates
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
package org.wildfly.uberjar.runtime;

import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author jdenise@redhat.com
 */
public class ArgumentsTestCase {

    @Test
    public void test() throws Exception {
        {
            String[] args = {};
            Arguments arguments = Arguments.parseArguments(args);
            assertNull(arguments.getExternalConfig());
            assertNull(arguments.getScriptFile());
            assertTrue(arguments.getServerArguments().isEmpty());
            assertNull(arguments.getServerDir());
            assertFalse(arguments.isHelp());
            assertFalse(arguments.isVersion());
            assertFalse(arguments.isNoDelete());
        }

        {
            Path config = Files.createTempFile(null, null);
            Path script = Files.createTempFile(null, null);
            Path deployment = Files.createTempFile(null, ".war");
            try {
                String[] args = {"--version", "--help",
                    "--server-config=" + config, "--cli-script=" + script, "--server-dir=foo",
                    "--no-delete", "--deployment=" + deployment
                };
                Arguments arguments = Arguments.parseArguments(args);
                assertEquals(arguments.getExternalConfig(), config);
                assertEquals(arguments.getScriptFile(), script);
                assertEquals(arguments.getDeployment(), deployment);
                assertEquals(1, arguments.getServerArguments().size());
                assertEquals(arguments.getServerDir(), "foo");
                assertTrue(arguments.isHelp());
                assertTrue(arguments.isVersion());
                assertTrue(arguments.isNoDelete());
            } finally {
                Files.delete(config);
                Files.delete(script);
            }
        }

        {
            boolean error = false;
            try {
                String[] args = {"--foo"};
                Arguments arguments = Arguments.parseArguments(args);
                error = true;
            } catch (Exception ex) {
                // OK expected
            }
            if (error) {
                throw new Exception("Should have failed");
            }
        }

        {
            boolean error = false;
            try {
                String[] args = {"--server-config=foo"};
                Arguments arguments = Arguments.parseArguments(args);
                error = true;
            } catch (Exception ex) {
                // OK expected
            }
            if (error) {
                throw new Exception("Should have failed");
            }
        }

        {
            boolean error = false;
            try {
                String[] args = {"--server-config=foo"};
                Arguments arguments = Arguments.parseArguments(args);
                error = true;
            } catch (Exception ex) {
                // OK expected
            }
            if (error) {
                throw new Exception("Should have failed");
            }
        }

        {
            boolean error = false;
            try {
                String[] args = {"--cli-script=foo"};
                Arguments arguments = Arguments.parseArguments(args);
                error = true;
            } catch (Exception ex) {
                // OK expected
            }
            if (error) {
                throw new Exception("Should have failed");
            }
        }

        {
            boolean error = false;
            try {
                String[] args = {"--deployment=foo"};
                Arguments arguments = Arguments.parseArguments(args);
                error = true;
            } catch (Exception ex) {
                // OK expected
            }
            if (error) {
                throw new Exception("Should have failed");
            }
        }

        {
            boolean error = false;
            try {
                String[] args = {"--cli-script="};
                Arguments arguments = Arguments.parseArguments(args);
                error = true;
            } catch (Exception ex) {
                // OK expected
            }
            if (error) {
                throw new Exception("Should have failed");
            }
        }

        {
            boolean error = false;
            try {
                String[] args = {"--server-config="};
                Arguments arguments = Arguments.parseArguments(args);
                error = true;
            } catch (Exception ex) {
                // OK expected
            }
            if (error) {
                throw new Exception("Should have failed");
            }
        }

        {
            Path config = Files.createTempFile(null, null);
            boolean error = false;
            try {
                String[] args = {"--server-config=" + config, "--server-config=" + config};
                Arguments arguments = Arguments.parseArguments(args);
                error = true;
            } catch (Exception ex) {
                // OK expected
            }
            if (error) {
                throw new Exception("Should have failed");
            }
        }
    }

}
