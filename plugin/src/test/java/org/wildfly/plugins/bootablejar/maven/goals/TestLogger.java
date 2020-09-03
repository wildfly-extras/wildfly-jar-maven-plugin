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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.codehaus.plexus.logging.AbstractLogger;
import org.codehaus.plexus.logging.Logger;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
class TestLogger extends AbstractLogger {
    private final org.jboss.logging.Logger delegate;
    private final Map<String, Logger> children;

    private TestLogger(final org.jboss.logging.Logger delegate) {
        super(Logger.LEVEL_INFO, delegate.getName());
        this.delegate = delegate;
        children = new ConcurrentHashMap<>();
    }

    static Logger getLogger(final Class<?> type) {
        return new TestLogger(org.jboss.logging.Logger.getLogger(type));
    }

    static Logger getLogger(final String name) {
        return new TestLogger(org.jboss.logging.Logger.getLogger(name));
    }

    @Override
    public void debug(final String message, final Throwable throwable) {
        delegate.debug(message, throwable);
    }

    @Override
    public void info(final String message, final Throwable throwable) {
        delegate.info(message, throwable);
    }

    @Override
    public void warn(final String message, final Throwable throwable) {
        delegate.warn(message, throwable);
    }

    @Override
    public void error(final String message, final Throwable throwable) {
        delegate.error(message, throwable);
    }

    @Override
    public void fatalError(final String message, final Throwable throwable) {
        delegate.fatal(message, throwable);
    }

    @Override
    public Logger getChildLogger(final String name) {
        return children.computeIfAbsent(name, TestLogger::getLogger);
    }
}
