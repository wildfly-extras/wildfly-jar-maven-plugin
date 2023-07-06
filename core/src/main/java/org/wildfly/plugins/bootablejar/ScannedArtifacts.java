/*
 * Copyright 2023 Red Hat, Inc. and/or its affiliates
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
package org.wildfly.plugins.bootablejar;

import java.util.Set;
import org.jboss.galleon.universe.maven.MavenArtifact;

/**
 *
 * @author jdenise
 */
public class ScannedArtifacts {

    private final MavenArtifact jbossModules;
    private final MavenArtifact boot;
    private final Set<MavenArtifact> cliArtifacts;

    public ScannedArtifacts(MavenArtifact bootArtifact, MavenArtifact jbossModules, Set<MavenArtifact> cliArtifacts) {
        this.boot = bootArtifact;
        this.jbossModules = jbossModules;
        this.cliArtifacts = cliArtifacts;
    }

    /**
     * @return the boot
     */
    public MavenArtifact getBoot() {
        return boot;
    }

    /**
     * @return the jbossModules
     */
    public MavenArtifact getJbossModules() {
        return jbossModules;
    }

    /**
     * @return the cliArtifacts
     */
    public Set<MavenArtifact> getCliArtifacts() {
        return cliArtifacts;
    }

}
