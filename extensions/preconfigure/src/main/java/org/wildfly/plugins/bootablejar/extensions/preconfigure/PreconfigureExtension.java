package org.wildfly.plugins.bootablejar.extensions.preconfigure;

import org.wildfly.core.jar.boot.RuntimeExtension;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Allows a bootable jar to access and run the extension/preconfigure script inside the
 * wildfly distribution.
 *
 * @author chrisruffalo
 */
public class PreconfigureExtension implements RuntimeExtension {

    /**
     * The name of the OS so that we can determine what type of script to look for/run.
     * This probably doesn't matter because the vast majority of use-cases will be
     * for linux or something that can interpret a shell script without prompting.
     */
    private static final String OS = System.getProperty("os.name").toLowerCase();

    /**
     * Simple method to determine if the bootable jar is running on windows
     *
     * @return true when running on windows, false otherwise
     */
    private boolean isWindows() {
        return OS.startsWith("windows");
    }

    @Override
    public void boot(List<String> list, Path path) throws Exception {
        // check extension directory exists
        final Path extensions = path.resolve("extensions");
        if (!Files.exists(extensions)) {
            System.err.println("no extension directory found in jboss home");
            return;
        }
        // check that the path to the actual script exists
        final Path preconfigure = extensions.resolve("preconfigure." + (isWindows() ? "bat" : "sh"));
        if (!Files.exists(preconfigure)) {
            System.err.printf("no preconfigure script (%s) found in preconfigure path%n", preconfigure.getFileName());
            return;
        }
        // before trying to execute the script, ensure that it is executable
        if (!Files.isExecutable(preconfigure)) {
            System.err.printf("the preconfiguration script (%s) is not executable", preconfigure);
            return;
        }

        // show what we are about to execute
        System.out.println("executing preconfigure: " + preconfigure.toString());

        // build a process
        final ProcessBuilder builder = new ProcessBuilder();
        builder.command(preconfigure.toString());
        // re-use environment and ensure jboss path matches the location of the script
        builder.environment().putAll(System.getenv());
        builder.environment().put("JBOSS_HOME", path.toString());

        // start the process and open the input/error streams so that the exact script can be relayed back
        // to the console/user/output
        final Process process = builder.start();
        try (
            final BufferedReader stdReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            final BufferedReader errReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        ) {
            // create threads that can independently read from each reader when ready
            final Thread std = reader(stdReader, System.out);
            final Thread err = reader(errReader, System.err);

            // start the reading threads so they will begin consuming and printing
            std.start();
            err.start();

            // wait for the process to end and collect the exit code, also wait
            // for the reader threads to end which will ensure that all the data
            // is read from the streams. (missing lines could cause confusion
            // for users if they do not get the full status/error)
            int exitVal = process.waitFor();
            std.join();
            err.join();
            System.out.println("finished with exit code: " + exitVal);
        }
    }

    /**
     * Create a threaded reader to read input from the reader so that the output from the process
     * shows up in the console int the correct order
     *
     * @param reader that serves as the source of the lines
     * @param outputStream to route the output to
     * @return a thread ready to be started
     */
    private Thread reader(final BufferedReader reader, final PrintStream outputStream) {
        return  new Thread(() -> {
            String line;
            while (true) {
                try {
                    if ((line = reader.readLine()) == null) break;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                outputStream.println(line);
            }
        });
    }
}
