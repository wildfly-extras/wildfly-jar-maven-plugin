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
package org.wildfly.galleon.uberjar;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import static org.wildfly.galleon.uberjar.Constants.B_ARG;
import static org.wildfly.galleon.uberjar.Constants.CLI_SCRIPT;
import static org.wildfly.galleon.uberjar.Constants.CLI_SCRIPT_PROP;
import static org.wildfly.galleon.uberjar.Constants.DEPLOYMENT;
import static org.wildfly.galleon.uberjar.Constants.DEPLOYMENT_PROP;
import static org.wildfly.galleon.uberjar.Constants.D_ARG;
import static org.wildfly.galleon.uberjar.Constants.EXTERNAL_SERVER_CONFIG;
import static org.wildfly.galleon.uberjar.Constants.EXTERNAL_SERVER_CONFIG_PROP;
import static org.wildfly.galleon.uberjar.Constants.HELP;
import static org.wildfly.galleon.uberjar.Constants.H_ARG;
import static org.wildfly.galleon.uberjar.Constants.NO_DELETE_SERVER_DIR;
import static org.wildfly.galleon.uberjar.Constants.NO_DELETE_SERVER_DIR_PROP;
import static org.wildfly.galleon.uberjar.Constants.SERVER_DIR;
import static org.wildfly.galleon.uberjar.Constants.SERVER_DIR_PROP;
import static org.wildfly.galleon.uberjar.Constants.U_ARG;
import static org.wildfly.galleon.uberjar.Constants.VERSION;
import static org.wildfly.galleon.uberjar.Constants.V_ARG;

/**
 *
 * @author jdenise
 */
public class Arguments {

    private Arguments() {

    }

    private String serverDir;
    private Boolean noDelete;
    private Path scriptFile;
    private Path externalConfig;
    private Boolean isHelp;
    private Boolean isVersion;
    private final List<String> serverArguments = new ArrayList<>();
    private Path deployment;

    public static Arguments parseArguments(String[] args) throws Exception {
        Objects.requireNonNull(args);
        Arguments arguments = new Arguments();
        arguments.handleArguments(args);
        arguments.handleSystemProperties();
        return arguments;
    }

    private void handleArguments(String[] args) throws Exception {
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith(EXTERNAL_SERVER_CONFIG)) {
                validateArg(externalConfig, EXTERNAL_SERVER_CONFIG);
                externalConfig = checkPath(getValue(a));
            } else if (a.startsWith(DEPLOYMENT)) {
                validateArg(deployment, DEPLOYMENT);
                deployment = checkPath(getValue(a));
            } else if (a.startsWith(CLI_SCRIPT)) {
                validateArg(scriptFile, CLI_SCRIPT);
                scriptFile = checkPath(getValue(a));
            } else if (a.startsWith(SERVER_DIR)) {
                validateArg(serverDir, SERVER_DIR);
                serverDir = getValue(a);
            } else if (NO_DELETE_SERVER_DIR.equals(a)) {
                validateArg(noDelete, NO_DELETE_SERVER_DIR);
                noDelete = true;
            } else if (a.startsWith(B_ARG)) {
                getServerArguments().add(a);
            } else if (a.startsWith(D_ARG)) {
                getServerArguments().add(a);
            } else if (a.startsWith(U_ARG)) {
                getServerArguments().add(a);
            } else if (a.equals(VERSION) || a.equals(V_ARG)) {
                validateArg(isVersion, VERSION);
                isVersion = true;
                serverArguments.add(a);
            } else if (a.equals(HELP) || a.equals(H_ARG)) {
                validateArg(isHelp, HELP);
                isHelp = true;
            } else {
                throw new Exception("Unknown argument " + a);
            }
        }
    }

    private void handleSystemProperties() throws Exception {
        for (String sysProp : System.getProperties().stringPropertyNames()) {
            String value = System.getProperty(sysProp);
            if (EXTERNAL_SERVER_CONFIG_PROP.equals(sysProp)) {
                validateArg(externalConfig, EXTERNAL_SERVER_CONFIG);
                externalConfig = checkPath(value);
            } else {
                if (DEPLOYMENT_PROP.equals(sysProp)) {
                    validateArg(deployment, DEPLOYMENT_PROP);
                    deployment = checkPath(value);
                } else {
                    if (CLI_SCRIPT_PROP.equals(sysProp)) {
                        validateArg(scriptFile, CLI_SCRIPT);
                        scriptFile = checkPath(value);
                    } else {
                        if (SERVER_DIR_PROP.equals(sysProp)) {
                            validateArg(serverDir, SERVER_DIR);
                            serverDir = value;
                        } else {
                            if (NO_DELETE_SERVER_DIR_PROP.equals(sysProp)) {
                                validateArg(noDelete, NO_DELETE_SERVER_DIR);
                                noDelete = true;
                            }
                        }
                    }
                }
            }
        }
    }

    private Path checkPath(String path) {
        Path filePath = Paths.get(path);
        if (!Files.exists(filePath)) {
            throw new RuntimeException("File " + path + " doesn't exist");
        }
        return filePath;
    }

    private void validateArg(Object val, String name) throws Exception {
        if (val != null) {
            throw new Exception("Argument " + name + " already set");
        }
    }

    private static String getValue(String arg) {
        int sep = arg.indexOf("=");
        if (sep == -1 || sep == arg.length() - 1) {
            throw new RuntimeException("Invalid argument " + arg + ", no value provided");
        }
        return arg.substring(sep + 1);
    }

    /**
     * @return the serverDir
     */
    public String getServerDir() {
        return serverDir;
    }

    /**
     * @return the noDelete
     */
    public Boolean isNoDelete() {
        return noDelete == null ? false : noDelete;
    }

    /**
     * @return the scriptFile
     */
    public Path getScriptFile() {
        return scriptFile;
    }

    /**
     * @return the externalConfig
     */
    public Path getExternalConfig() {
        return externalConfig;
    }

    /**
     * @return the isHelp
     */
    public Boolean isHelp() {
        return isHelp == null ? false : isHelp;
    }

    /**
     * @return the isVersion
     */
    public Boolean isVersion() {
        return isVersion == null ? false : isVersion;
    }

    /**
     * @return the serverArguments
     */
    public List<String> getServerArguments() {
        return Collections.unmodifiableList(serverArguments);
    }

    /**
     * @return the deployment
     */
    public Path getDeployment() {
        return deployment;
    }

}
