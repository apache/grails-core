/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.init;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.jar.JarFile;

/**
 * The class the wrapper starts in a downloaded {@code grails-cli} jar.
 * <p>
 * The wrapper jar is committed in every application and outlives the version of Grails it was
 * generated with, so it only downloads: it starts {@link #NAME}, which every {@code grails-cli} jar
 * since Grails 7 has, and that class starts the command line of its jar, wherever a version of Grails has it.
 */
public final class StartClass {

    /**
     * The class the wrapper starts, by the name the wrapper of Grails 7 and 8 starts it by too.
     */
    public static final String NAME = "org.apache.grails.cli.DelegatingShellApplication";

    /**
     * The system property the wrapper sets to its version before it starts {@link #NAME}: the command line
     * of Grails 9 and later then knows that the wrapper is not one of Grails 7 or 8, which it replaces.
     */
    public static final String WRAPPER_VERSION_PROPERTY = "grails.wrapper.version";

    private static final String ENTRY = NAME.replace('.', '/') + ".class";

    // Of the systems the wrapper runs on, only Windows refuses to open a file that is there for a moment: while
    // another wrapper moves its download into the place of the jar, the move holds the file in a way an open by a
    // reader does not share
    private static final boolean WINDOWS = File.separatorChar == '\\';
    private static final long OPEN_RETRY_MILLIS = 30_000;
    private static final long OPEN_PAUSE_MILLIS = 50;

    private StartClass() {
    }

    /**
     * Says why a file cannot be started as a {@code grails-cli} jar: it is not there, it does not open as a jar
     * (which a file that was cut off does not, since the directory of a jar is at its end), or it does not have
     * the class it would be started with.
     *
     * @param cliJar a {@code grails-cli} jar
     * @return what is wrong with the file, or null when it can be started
     */
    public static String defect(File cliJar) {
        if (!cliJar.isFile()) {
            return "it is not a file";
        }
        try (JarFile jar = open(cliJar)) {
            return jar.getEntry(ENTRY) != null ? null : "it has no start class [" + NAME + "]";
        } catch (IOException | RuntimeException e) {
            // the text of the JDK, and never content of the file
            return "it does not open as a jar: " + e.getClass().getSimpleName();
        }
    }

    /**
     * Loads the start class of a {@code grails-cli} jar from a class loader of that jar.
     *
     * @param cliJar a {@code grails-cli} jar
     * @return the start class, defined by the jar
     * @throws IOException           if the jar cannot be read
     * @throws IllegalStateException if the jar has no start class, or the class path of the wrapper has one
     */
    public static Class<?> load(File cliJar) throws IOException {
        URLClassLoader loader = new URLClassLoader(new URL[]{cliJar.toURI().toURL()});
        Class<?> startClass;
        // the jar is held open while the class loader opens it: on Windows no other wrapper can then move its
        // download into the place of the jar, which would keep the class loader from opening it, and the class
        // loader takes a jar it cannot open for one without the class
        try (JarFile held = open(cliJar)) {
            startClass = Class.forName(NAME, true, loader);
        } catch (ClassNotFoundException | IOException e) {
            throw new IllegalStateException("The Grails command line at [" + cliJar.getAbsolutePath() + "] has no start class [" + NAME + "]. " +
                    "It is not a version of Grails this wrapper can start, or the download is incomplete: delete the file and run the command again.", e);
        }
        // the parent is asked first: a class of that name on the class path of the wrapper would not start the downloaded command line
        if (startClass.getClassLoader() != loader) {
            throw new IllegalStateException("The start class [" + NAME + "] of the Grails command line at [" + cliJar.getAbsolutePath() + "] is not a class of that file.");
        }
        return startClass;
    }

    /**
     * Opens a jar. On Windows an open fails as if the file were not there while another wrapper moves its
     * download into the place of the jar; it is tried again until the file opens, for a while.
     */
    private static JarFile open(File cliJar) throws IOException {
        long deadline = System.currentTimeMillis() + OPEN_RETRY_MILLIS;
        while (true) {
            try {
                return new JarFile(cliJar);
            } catch (FileNotFoundException e) {
                if (!WINDOWS || !cliJar.isFile() || System.currentTimeMillis() > deadline) {
                    throw e;
                }
                try {
                    Thread.sleep(OPEN_PAUSE_MILLIS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }
}
