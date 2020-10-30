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

package org.wildfly.plugins.bootablejar.maven.common;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@RunWith(Parameterized.class)
public class ArgumentParsingTestCase {
    private static final List<String> EXPECTED_VALUES = Arrays.asList(
            "-Dtest.prop1=value1",
            "-Dtest.prop2=value2",
            "-b",
            "0.0.0.0",
            "-b=0.0.0.0",
            "-Dtest.path=\"C:\\Users\\test user\\tmp\"",
            "--agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005"
    );

    @Parameterized.Parameter
    public String separator;

    @Parameterized.Parameters
    public static Collection<Object> data() {
        return Arrays.asList(
                " ",
                "\n",
                "\r\n",
                "\t"
        );
    }

    @Test
    public void testSplit() {
        final List<String> parameters = Utils.splitArguments(createParameters(separator));
        Assert.assertEquals(String.format("Expected:%n\t%s%nFound:%n\t%s", EXPECTED_VALUES, parameters), EXPECTED_VALUES.size(), parameters.size());
        for (int i = 0; i < EXPECTED_VALUES.size(); i++) {
            Assert.assertEquals(EXPECTED_VALUES.get(i), parameters.get(i));
        }
    }

    private static CharSequence createParameters(final String separator) {
        final StringBuilder result = new StringBuilder();
        final Iterator<String> iterator = EXPECTED_VALUES.iterator();
        while (iterator.hasNext()) {
            result.append(iterator.next());
            if (iterator.hasNext()) {
                result.append(separator);
            }
        }
        return result;
    }
}
