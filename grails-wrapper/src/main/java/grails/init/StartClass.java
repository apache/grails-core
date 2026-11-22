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
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.regex.Pattern;

/**
 * Finds the class that starts the command line in a downloaded {@code grails-cli} jar.
 * <p>
 * The wrapper jar is committed in every application and outlives the version of Grails it was
 * generated with, so the name of the start class is not compiled into it: the jar says it in the
 * {@code Main-Class} attribute of its manifest, as every {@code grails-cli} jar since Grails 7 does.
 * {@link #DEFAULT} is used when the jar does not say, or says something that is not a class of the jar.
 */
public final class StartClass {

    /**
     * The class that starts the command line since Grails 9.
     */
    public static final String DEFAULT = "org.apache.grails.cli.GrailsCli";

    private static final int MAX_NAME_LENGTH = 512;
    private static final Pattern BINARY_NAME = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*");

    private StartClass() {
    }

    /**
     * @param cliJar a {@code grails-cli} jar
     * @return the {@code Main-Class} of the manifest of the jar when it names a class the jar has, {@link #DEFAULT} otherwise
     * @throws IOException if the jar cannot be read
     */
    public static String resolve(File cliJar) throws IOException {
        try (JarFile jar = new JarFile(cliJar)) {
            String declared = declaredMainClass(jar.getManifest());
            if (declared != null && isBinaryName(declared) && jar.getEntry(declared.replace('.', '/') + ".class") != null) {
                return declared;
            }
            return DEFAULT;
        }
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
        try (JarFile jar = new JarFile(cliJar)) {
            String declared = declaredMainClass(jar.getManifest());
            String name = declared != null && isBinaryName(declared) && jar.getEntry(declared.replace('.', '/') + ".class") != null ? declared : DEFAULT;
            return jar.getEntry(name.replace('.', '/') + ".class") != null ? null : "it has no start class [" + name + "]";
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
     * @throws IllegalStateException if the jar has no start class, or the name is that of a class of the wrapper or of the JDK
     */
    public static Class<?> load(File cliJar) throws IOException {
        String name = resolve(cliJar);
        URLClassLoader loader = new URLClassLoader(new URL[]{cliJar.toURI().toURL()});
        Class<?> startClass;
        try {
            startClass = Class.forName(name, true, loader);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("The Grails command line at [" + cliJar.getAbsolutePath() + "] has no start class [" + name + "]. " +
                    "It is not a version of Grails this wrapper can start, or the download is incomplete: delete the file and run the command again.", e);
        }
        // the parent is asked first: a name the wrapper or the JDK also has would not start the downloaded command line
        if (startClass.getClassLoader() != loader) {
            throw new IllegalStateException("The start class [" + name + "] of the Grails command line at [" + cliJar.getAbsolutePath() + "] is not a class of that file.");
        }
        return startClass;
    }

    private static String declaredMainClass(Manifest manifest) {
        if (manifest == null) {
            return null;
        }
        String value = manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
        return value == null ? null : value.trim();
    }

    private static boolean isBinaryName(String name) {
        return name.length() <= MAX_NAME_LENGTH && BINARY_NAME.matcher(name).matches();
    }
}
