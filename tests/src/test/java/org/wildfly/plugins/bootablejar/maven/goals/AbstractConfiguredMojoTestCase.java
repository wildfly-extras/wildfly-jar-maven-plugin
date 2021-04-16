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
package org.wildfly.plugins.bootablejar.maven.goals;

import java.io.File;
import java.util.Arrays;
import org.apache.maven.DefaultMaven;
import org.apache.maven.Maven;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.MavenArtifactRepository;
import org.apache.maven.artifact.repository.layout.DefaultRepositoryLayout;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.Mojo;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingRequest;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.internal.impl.SimpleLocalRepositoryManagerFactory;
import org.eclipse.aether.repository.LocalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A class to construct a properly configured MOJO.
 *
 */
public abstract class AbstractConfiguredMojoTestCase extends AbstractMojoTestCase {
    private static final String ARTIFACTID = "wildfly-jar-maven-plugin";

    protected MavenSession newMavenSession() {
        try {
            MavenExecutionRequest request = new DefaultMavenExecutionRequest();
            MavenExecutionResult result = new DefaultMavenExecutionResult();

            MavenExecutionRequestPopulator populator;
            populator = getContainer().lookup(MavenExecutionRequestPopulator.class);
            populator.populateDefaults(request);
            // Required otherwise WARNING:The POM for org.wildfly.core:wildfly-jar-boot:jar:
            // is invalid, transitive dependencies (if any)
            request.setSystemProperties(System.getProperties());

            DefaultMaven maven = (DefaultMaven) getContainer().lookup(Maven.class);
            DefaultRepositorySystemSession repoSession
                    = (DefaultRepositorySystemSession) maven.newRepositorySession(request);

            // Add remote repositories required to resolve provisioned artifacts.
            ArtifactRepositoryPolicy snapshot = new ArtifactRepositoryPolicy(false, ArtifactRepositoryPolicy.UPDATE_POLICY_NEVER, ArtifactRepositoryPolicy.CHECKSUM_POLICY_IGNORE);
            ArtifactRepositoryPolicy release = new ArtifactRepositoryPolicy(true, ArtifactRepositoryPolicy.UPDATE_POLICY_DAILY, ArtifactRepositoryPolicy.CHECKSUM_POLICY_IGNORE);

            // Take into account maven.repo.local
            String path = System.getProperty("maven.repo.local", request.getLocalRepository().getBasedir());
            repoSession.setLocalRepositoryManager(
                    new SimpleLocalRepositoryManagerFactory().newInstance(repoSession,
                            new LocalRepository(path)));
            request.addRemoteRepository(new MavenArtifactRepository("jboss", "https://repository.jboss.org/nexus/content/groups/public/",
                    new DefaultRepositoryLayout(), snapshot, release));
            request.addRemoteRepository(new MavenArtifactRepository("redhat-ga", "https://maven.repository.redhat.com/ga/",
                    new DefaultRepositoryLayout(), snapshot, release));

            @SuppressWarnings("deprecation")
            MavenSession session = new MavenSession(getContainer(),
                    repoSession,
                    request, result);
            return session;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected MavenSession newMavenSession(MavenProject project) {
        MavenSession session = newMavenSession();
        session.setCurrentProject(project);
        session.setProjects(Arrays.asList(project));
        return session;
    }

    protected Mojo lookupConfiguredMojo(File pom, String goal) throws Exception {
        assertNotNull(pom);
        assertTrue(pom.exists());

        ProjectBuildingRequest buildingRequest = newMavenSession().getProjectBuildingRequest();
        // Need to resolve artifacts for tests that upgrade server components
        buildingRequest.setResolveDependencies(true);
        ProjectBuilder projectBuilder = lookup(ProjectBuilder.class);
        MavenProject project = projectBuilder.build(pom, buildingRequest).getProject();


        Mojo mojo = lookupConfiguredMojo(project, goal);
        // We need to set a logger, the SystemStreamLog is incompatible with Galleon.
        // There is an interface implementation mismatch that leads to NPE (exception is not checked for null).
        Logger log = LoggerFactory.getLogger("bootable.jar.test");
        mojo.setLog(new Log(){
            @Override
            public boolean isDebugEnabled() {
                return log.isDebugEnabled();
            }

            @Override
            public void debug(CharSequence cs) {
                log.debug(cs == null ? null : String.valueOf(cs));
            }

            @Override
            public void debug(CharSequence cs, Throwable thrwbl) {
                log.debug(cs == null ? null : String.valueOf(cs), thrwbl);
            }

            @Override
            public void debug(Throwable thrwbl) {
                log.debug(null, thrwbl);
            }

            @Override
            public boolean isInfoEnabled() {
                return log.isInfoEnabled();
            }

            @Override
            public void info(CharSequence cs) {
                log.info(cs == null ? null : String.valueOf(cs));
            }

            @Override
            public void info(CharSequence cs, Throwable thrwbl) {
                log.info(cs == null ? null : String.valueOf(cs), thrwbl);
            }

            @Override
            public void info(Throwable thrwbl) {
                log.info(null, thrwbl);
            }

            @Override
            public boolean isWarnEnabled() {
                return log.isWarnEnabled();
            }

            @Override
            public void warn(CharSequence cs) {
                log.warn(cs == null ? null : String.valueOf(cs));
            }

            @Override
            public void warn(CharSequence cs, Throwable thrwbl) {
                log.warn(cs == null ? null : String.valueOf(cs), thrwbl);
            }

            @Override
            public void warn(Throwable thrwbl) {
                log.warn(null, thrwbl);
            }

            @Override
            public boolean isErrorEnabled() {
                return log.isErrorEnabled();
            }

            @Override
            public void error(CharSequence cs) {
                log.error(cs == null ? null : String.valueOf(cs));
            }

            @Override
            public void error(CharSequence cs, Throwable thrwbl) {
                log.error(cs == null ? null : String.valueOf(cs), thrwbl);
            }

            @Override
            public void error(Throwable thrwbl) {
                log.error(null, thrwbl);
            }
        });
        // For some reasons, the configuration item gets ignored in lookupConfiguredMojo
        // explicitly configure it
        configureMojo(mojo, ARTIFACTID, pom);

        return mojo;
    }

}
