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
package org.wildfly.plugins.bootablejar.extensions.cloud;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author jdenise
 */
public class CloudExtensionTestCase {

    @Test
    public void test() throws Exception {
        CloudExtension extension = new CloudExtension();
        Path p = Files.createTempDirectory("cloud-ext-test");
        p.toFile().deleteOnExit();
        List<String> args = new ArrayList<>();
        extension.doBoot(args, p, "abcdefghijklmnopqrstvuwxyz");
        assertTrue(args.size() == 2);
        assertTrue(args.toString(), "-Dfoo.bar=toto".equals(args.get(0)));
        assertTrue(args.toString(), "-Djboss.node.name=defghijklmnopqrstvuwxyz".equals(args.get(1)));
    }

    @Test
    public void test2() throws Exception {
        CloudExtension extension = new CloudExtension();
        Path p = Files.createTempDirectory("cloud-ext-test");
        p.toFile().deleteOnExit();
        List<String> args = new ArrayList<>();
        extension.doBoot(args, p, "abcdefghijklmnopqrstvuw");
        assertTrue(args.size() == 2);
        assertTrue(args.toString(), "-Dfoo.bar=toto".equals(args.get(0)));
        assertTrue(args.toString(), "-Djboss.node.name=abcdefghijklmnopqrstvuw".equals(args.get(1)));
    }

    @Test
    public void test3() throws Exception {
        CloudExtension extension = new CloudExtension();
        Path p = Files.createTempDirectory("cloud-ext-test");
        p.toFile().deleteOnExit();
        List<String> args = new ArrayList<>();
        extension.doBoot(args, p, "a");
        assertTrue(args.size() == 2);
        assertTrue(args.toString(), "-Dfoo.bar=toto".equals(args.get(0)));
        assertTrue(args.toString(), "-Djboss.node.name=a".equals(args.get(1)));
    }

    @Test
    public void test4() throws Exception {
        CloudExtension extension = new CloudExtension();
        Path p = Files.createTempDirectory("cloud-ext-test");
        p.toFile().deleteOnExit();
        List<String> args = new ArrayList<>();
        args.add("-Djboss.node.name=foo");
        extension.doBoot(args, p, "abcdef");
        assertTrue(args.size() == 2);
        assertTrue(args.toString(), "-Djboss.node.name=foo".equals(args.get(0)));
        assertTrue(args.toString(), "-Dfoo.bar=toto".equals(args.get(1)));
    }

    @Test
    public void test5() throws Exception {
        CloudExtension extension = new CloudExtension();
        Path p = Files.createTempDirectory("cloud-ext-test");
        p.toFile().deleteOnExit();
        List<String> args = new ArrayList<>();
        System.setProperty("jboss.node.name", "foo");
        try {
            extension.doBoot(args, p, "abcdef");
            assertTrue(args.size() == 1);
            assertTrue(args.toString(), "-Dfoo.bar=toto".equals(args.get(0)));
        } finally {
            System.clearProperty("jboss.node.name");
        }
    }

    @Test
    public void test6() throws Exception {
        CloudExtension extension = new CloudExtension();
        Path p = Files.createTempDirectory("cloud-ext-test");
        p.toFile().deleteOnExit();
        List<String> args = new ArrayList<>();
        args.add("-Dfoo.bar=fromtest");
        extension.doBoot(args, p, "abcdef");
        assertTrue(args.size() == 2);
        assertTrue(args.toString(), "-Dfoo.bar=fromtest".equals(args.get(0)));
        assertTrue(args.toString(), "-Djboss.node.name=abcdef".equals(args.get(1)));
    }
}
