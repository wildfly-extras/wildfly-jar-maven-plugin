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

/**
 *
 * @author jdenise
 */
class Constants {

    public static final String JBOSS_SERVER_CONFIG_DIR = "jboss.server.config.dir";
    public static final String JBOSS_SERVER_LOG_DIR = "jboss.server.log.dir";

    public static final String UBERJAR = "org.wildfly.uberjar.";
    public static final String DEPLOYMENT = "--deployment";
    public static final String DEPLOYMENT_PROP = UBERJAR + "deployment";
    public static final String EXTERNAL_SERVER_CONFIG_PROP = UBERJAR + "server.config";
    public static final String EXTERNAL_SERVER_CONFIG = "--server-config";
    public static final String CLI_SCRIPT = "--cli-script";
    public static final String CLI_SCRIPT_PROP = UBERJAR + "cli.script";
    public static final String SERVER_DIR = "--server-dir";
    public static final String SERVER_DIR_PROP = UBERJAR + "server.dir";
    public static final String NO_DELETE_SERVER_DIR = "--no-delete";
    public static final String NO_DELETE_SERVER_DIR_PROP = UBERJAR + "no.delete";

    public static final String B_ARG = "-b";
    public static final String D_ARG = "-D";
    public static final String H_ARG = "-h";
    public static final String U_ARG = "-u";
    public static final String V_ARG = "-v";
    public static final String VERSION = "--version";
    public static final String HELP = "--help";

    public static final String LOG_MANAGER_PROP = "java.util.logging.manager";
    public static final String LOG_MANAGER_CLASS = "org.jboss.logmanager.LogManager";
    public static final String LOG_BOOT_FILE_PROP = "org.jboss.boot.log.file";
}
