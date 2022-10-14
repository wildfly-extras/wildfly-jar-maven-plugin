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

package org.wildfly.plugins.demo.logging.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;
import jakarta.annotation.Resource;
import jakarta.enterprise.concurrent.ManagedScheduledExecutorService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.wildfly.plugins.demo.logging.model.LogMessage;

/**
 * A singleton resource for managing logging jobs.
 *
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@ApplicationScoped
public class ScheduledLogger {

    @Inject
    private Logger logger;

    @Resource
    private ManagedScheduledExecutorService executor;

    private final Map<String, Future<?>> jobs = Collections.synchronizedMap(new LinkedHashMap<>());
    private final AtomicInteger jobCounter = new AtomicInteger();

    @PreDestroy
    public void destroy() {
        stopAll();
    }

    /**
     * Logs a single message.
     *
     * @param logMessage the message to log
     */
    public void log(final LogMessage logMessage) {
        final Throwable cause = logMessage.isAddException() ? new RuntimeException("This is an example cause.") : null;
        final Logger.Level level = parseLevel(logMessage.getLevel());
        logger.log(level, logMessage.getMessage(), cause);
    }

    /**
     * Starts a logging job which logs a message every 5 seconds.
     *
     * @return the job id
     */
    public String start() {
        return start(5);
    }

    /**
     * Starts a logging job which logs a message for the defined seconds.
     *
     * @param seconds the seconds to schedule the jobs for
     *
     * @return the job id
     */
    public String start(final long seconds) {
        final String jobId = generateJobId();
        final AtomicInteger counter = new AtomicInteger();
        final Runnable r = () -> logger.infof("Log number %d from job id %s", counter.incrementAndGet(), jobId);
        jobs.put(jobId, executor.scheduleAtFixedRate(r, 0, seconds, TimeUnit.SECONDS));
        return jobId;
    }

    /**
     * Stops a job.
     *
     * @param id the job id
     *
     * @return {@code true} if the job was successfully cancelled
     *
     * @see Future#cancel(boolean)
     */
    public boolean stop(final String id) {
        final Future<?> future = jobs.remove(id);
        if (future == null) {
            return false;
        }
        return future.cancel(true);
    }

    /**
     * Stops all jobs.
     *
     * @return a map of the job id's that were stopped and boolean of whether or not the job was successfully cancelled
     *
     * @see Future#cancel(boolean)
     */
    public Map<String, Boolean> stopAll() {
        final Map<String, Boolean> stoppedJobs = new HashMap<>();
        synchronized (jobs) {
            final Iterator<Map.Entry<String, Future<?>>> iter = jobs.entrySet().iterator();
            while (iter.hasNext()) {
                final Map.Entry<String, Future<?>> entry = iter.next();
                stoppedJobs.put(entry.getKey(), entry.getValue().cancel(true));
                iter.remove();
            }
        }
        return stoppedJobs;
    }

    /**
     * Returns a collection of the running jobs.
     *
     * @return a collection of the running jobs
     */
    public Collection<String> runningJobs() {
        synchronized (jobs) {
            return new ArrayList<>(jobs.keySet());
        }
    }

    private String generateJobId() {
        return "log-job-" + jobCounter.incrementAndGet();
    }

    private static Logger.Level parseLevel(final String level) {
        for (Logger.Level l : Logger.Level.values()) {
            if (l.name().equalsIgnoreCase(level)) {
                return l;
            }
        }
        return Logger.Level.INFO;
    }
}
