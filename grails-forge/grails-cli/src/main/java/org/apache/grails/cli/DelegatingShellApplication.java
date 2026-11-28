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
package org.apache.grails.cli;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * What every Grails wrapper starts in the command line it downloads, {@code grails-cli-VERSION-all.jar}.
 * The name of this class is compiled into {@code grails-wrapper.jar}, the file of the wrapper that is
 * committed in an application, by every version of Grails since 7, so it is kept for good, and this class
 * does no more than start the command line of its jar: the class the {@code Main-Class} attribute of the
 * manifest of the jar names. It names no class of the command line and needs nothing but the JDK, and it is
 * the only class of its package.
 *
 * <p>The wrapper of Grails 9 and later says that it started this class: it sets the system property
 * {@value #WRAPPER_VERSION} to its version first. The command line is then started with the arguments it
 * is given, and nothing else is done.</p>
 *
 * <p>The wrapper of Grails 7 and 8 sets nothing, and neither does a tool that starts this class. For them
 * this class sets the system property {@value #EARLIER_WRAPPER} to {@code true} before it starts the
 * command line, which then replaces the wrapper of the project by that of its version first: in a Grails
 * project that has a wrapper it runs {@code update-wrapper}, which replaces {@code grailsw},
 * {@code grailsw.bat} and {@code grails-wrapper.jar}, and then the command line it was given. The wrapper
 * that is written sets the property and does not come to that step again. The earlier wrapper takes
 * {@code update-wrapper} from the front of the command line before it starts this class, so a command line
 * without arguments is what is left of {@code ./grailsw update-wrapper}: where the wrapper was looked at,
 * the JVM ends once that is said, and the interactive console is not started.</p>
 *
 * <p>The JVM ends with the exit code of the command line, which ends it. What the command line throws is
 * thrown by {@link #main} as it is.</p>
 *
 * @since 9.0
 */
public final class DelegatingShellApplication {

    /**
     * The system property the Grails wrapper of Grails 9 and later sets, to its version, before it calls
     * {@link #main}.
     */
    public static final String WRAPPER_VERSION = "grails.wrapper.version";

    /**
     * The system property this class sets to {@code true} when the system property {@value #WRAPPER_VERSION}
     * is not set, and clears when it is, before it starts the command line: the command line replaces the
     * wrapper of the project first when it is {@code true}.
     */
    public static final String EARLIER_WRAPPER = "grails.cli.earlierWrapper";

    private DelegatingShellApplication() {
    }

    /**
     * Starts the command line of the jar this class is in with the arguments.
     *
     * @param args the command line, as the wrapper passes it on
     * @throws Throwable what the command line throws, as it is
     * @throws IllegalStateException if this class is not in a jar whose manifest names the command line
     */
    public static void main(String[] args) throws Throwable {
        if (System.getProperty(WRAPPER_VERSION) == null) {
            System.setProperty(EARLIER_WRAPPER, "true");
        } else {
            System.clearProperty(EARLIER_WRAPPER);
        }
        Class<?> commandLine = Class.forName(commandLine(), true, DelegatingShellApplication.class.getClassLoader());
        MethodHandle main = MethodHandles.publicLookup().findStatic(commandLine, "main", MethodType.methodType(void.class, String[].class));
        main.invokeExact(args);
    }

    /**
     * The jar of this class is open already, in the class loader that loaded it, so reading its manifest
     * again reads no more than the manifest.
     */
    private static String commandLine() {
        CodeSource source = DelegatingShellApplication.class.getProtectionDomain().getCodeSource();
        URL location = source == null ? null : source.getLocation();
        Path jar;
        try {
            jar = location == null ? null : Path.of(location.toURI());
        } catch (URISyntaxException | IllegalArgumentException | FileSystemNotFoundException e) {
            jar = null;
        }
        if (jar == null) {
            throw new IllegalStateException(DelegatingShellApplication.class.getName() + " was not loaded from a file [" + location + "]: it starts the Main-Class of the jar it is in");
        }
        String name;
        try (JarFile file = new JarFile(jar.toFile())) {
            Manifest manifest = file.getManifest();
            name = manifest == null ? null : manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
        } catch (IOException e) {
            throw new IllegalStateException("The Grails command line at [" + jar + "] does not open as a jar: " + e, e);
        }
        name = name == null ? "" : name.trim();
        if (name.isEmpty()) {
            throw new IllegalStateException("The Grails command line at [" + jar + "] names no Main-Class in its manifest, and " +
                    DelegatingShellApplication.class.getName() + " starts that class");
        }
        if (name.equals(DelegatingShellApplication.class.getName())) {
            throw new IllegalStateException("The Grails command line at [" + jar + "] names " + name + " as its Main-Class, which would start itself");
        }
        return name;
    }
}
