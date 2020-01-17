/*
 * Copyright 2016-2020 Red Hat, Inc. and/or its affiliates
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

import java.io.PrintStream;

import org.jboss.as.process.CommandLineArgumentUsage;
import org.jboss.as.process.CommandLineConstants;
import org.wildfly.uberjar.runtime._private.UberJarLogger;

/**
 *
 * @author jdenise
 */
public class CmdUsage extends CommandLineArgumentUsage {
    public static void init() {
        addArguments(CommandLineConstants.PUBLIC_BIND_ADDRESS + " <value>", CommandLineConstants.PUBLIC_BIND_ADDRESS + "=<value>");
        instructions.add(UberJarLogger.ROOT_LOGGER.argPublicBindAddress());

        addArguments(CommandLineConstants.PUBLIC_BIND_ADDRESS + "<interface>=<value>");
        instructions.add(UberJarLogger.ROOT_LOGGER.argInterfaceBindAddress());

        addArguments(CommandLineConstants.DEFAULT_MULTICAST_ADDRESS + " <value>", CommandLineConstants.DEFAULT_MULTICAST_ADDRESS + "=<value>");
        instructions.add(UberJarLogger.ROOT_LOGGER.argDefaultMulticastAddress());

        addArguments(CommandLineConstants.SHORT_PROPERTIES + " <url>", CommandLineConstants.SHORT_PROPERTIES + "=<url>", CommandLineConstants.PROPERTIES + "=<url>");
        instructions.add(UberJarLogger.ROOT_LOGGER.argProperties());

        addArguments(CommandLineConstants.SYS_PROP + "<name>[=<value>]");
        instructions.add(UberJarLogger.ROOT_LOGGER.argSystem());

        addArguments(CommandLineConstants.SHORT_HELP, CommandLineConstants.HELP);
        instructions.add(UberJarLogger.ROOT_LOGGER.argHelp());

        addArguments(CommandLineConstants.START_MODE);
        instructions.add(UberJarLogger.ROOT_LOGGER.argStartMode());

        addArguments(CommandLineConstants.SHORT_VERSION, CommandLineConstants.VERSION);
        instructions.add(UberJarLogger.ROOT_LOGGER.argVersion());
    }
    public static void printUsage(final PrintStream out) {
        init();
        out.print(usageUberJar());
    }
}
