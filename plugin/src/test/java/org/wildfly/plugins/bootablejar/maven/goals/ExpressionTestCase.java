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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class ExpressionTestCase {

    @Test
    public void testExpressions() {
        testExpression("${jboss.server.log.dir}/server.log", "jboss.server.log.dir", null);
        testExpression("${jboss.server.log.dir:C:\\User\\default\\}\\server.log",
                "jboss.server.log.dir", "C:\\User\\default\\");

        testExpression("${test.level:DEBUG}", "test.level", "DEBUG");

        testExpression("${test$$.no-default}", "test$$.no-default", null);

        testExpression("${key1,key2,key3}", Arrays.asList("key1", "key2", "key3"), null);
        testExpression("${key1,key2,key3:defaultValue}", Arrays.asList("key1", "key2", "key3"), "defaultValue");

        String toTest = "${test.log.dir,jboss.server.log.dir:/var/log}/${test.path}/test.log";
        Map<Collection<String>, String> expectedResults = new LinkedHashMap<>();
        expectedResults.put(Arrays.asList("test.log.dir", "jboss.server.log.dir"), "/var/log");
        expectedResults.put(Collections.singletonList("test.path"), null);
        testExpression(toTest, expectedResults);
    }

    private void testExpression(final String toTest, final String expectedKey, final String expectedDefaultValue) {
        testExpression(toTest, Collections.singletonMap(Collections.singletonList(expectedKey), expectedDefaultValue));
    }

    private void testExpression(final String toTest, final Collection<String> expectedKeys, final String expectedDefaultValue) {
        testExpression(toTest, Collections.singletonMap(expectedKeys, expectedDefaultValue));
    }

    private void testExpression(final String toTest, final Map<Collection<String>, String> expected) {
        final Collection<Expression> expressions = Expression.parse(toTest);
        if (expected.size() != expressions.size()) {
            final String msg = String.format("Found results don't match the expected results.%nPattern: %s%nFound: %s%nExpected: %s%n",
                    toTest, expressions, expected);
            Assert.fail(msg);

        }
        final Iterator<Map.Entry<Collection<String>, String>> iter = expected.entrySet().iterator();
        for (Expression expression : expressions) {
            final Map.Entry<Collection<String>, String> entry = iter.next();
            Assert.assertEquals(entry.getKey(), expression.getKeys());
            Assert.assertEquals(entry.getValue(), expression.getDefaultValue());
        }
    }
}
