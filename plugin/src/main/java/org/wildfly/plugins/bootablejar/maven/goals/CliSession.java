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

import java.util.Collections;
import java.util.List;

/**
 * A CLI execution session.
 * @author jdenise
 */
public class CliSession {

    private List<String> scriptFiles = Collections.emptyList();
    private String propertiesFile;
    boolean resolveExpressions = true;

    /**
     * Set the list of CLI script files to execute.
     *
     * @param scriptFiles List of script file paths.
     */
    public void setScriptFiles(List<String> scriptFiles) {
        this.scriptFiles = scriptFiles;
    }

    /**
     * Get the list of CLI script files to execute.
     *
     * @return The list of file paths.
     */
    public List<String> getScriptFiles() {
        return scriptFiles;
    }

    /**
     * Set the properties file used when executing the CLI.
     *
     * @param propertiesFile Path to properties file.
     */
    public void setPropertiesFile(String propertiesFile) {
        this.propertiesFile = propertiesFile;
    }

    /**
     * Get the properties file used when executing the CLI.
     *
     * @return The properties file path.
     */
    public String getPropertiesFile() {
        return propertiesFile;
    }

    /**
     * By default, the CLI resolves expressions located in scripts locally. In order to have the expressions
     * resolved at server execution time, set this value to false.
     * @param resolveExpressions True to resolve locally, false to resolve at server execution time.
     */
    public void setResolveExpressions(boolean resolveExpressions) {
        this.resolveExpressions = resolveExpressions;
    }

    /**
     * Get the expression resolution value.
     * @return The expression resolution value.
     */
    public boolean getResolveExpression() {
        return resolveExpressions;
    }

    @Override
    public String toString() {
        return "CLI Session, scripts=" + this.scriptFiles +
                ", resolve-expressions="+this.resolveExpressions +", properties-file="+this.propertiesFile;
    }
}
