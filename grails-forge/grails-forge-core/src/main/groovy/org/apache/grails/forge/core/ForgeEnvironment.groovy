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
package org.apache.grails.forge.core

import grails.util.Environment
import groovy.transform.CompileStatic

/**
 * The Grails environment of Forge itself, which is always production.
 *
 * <p>Forge is a Grails application of its own. The command line runs it, and so do the tools the
 * Grails Gradle plugin starts to check a feature pack and to generate its fixtures. It runs on a
 * developer's machine to generate projects for development, but Forge is not one of those projects
 * and is not under development there: it is a finished application, so it runs as production. The
 * environment set here is Forge's, not the user's, and it is not something a user sets: a
 * {@code -Dgrails.env} or {@code GRAILS_ENV} that reaches the JVM of Forge was meant for a project,
 * so Forge sets its own environment over it. It says nothing about the configuration of the user's
 * project either. The environment of a project is the one a command that runs the project's Gradle
 * build gives that build, and where such a command reads the project's configuration, it reads it for
 * that environment.</p>
 *
 * <p>In development Grails watches the working directory, which is the user's project, for source
 * changes to reload. The templates of feature packs do not depend on the environment:
 * {@link org.apache.grails.forge.core.pack.PackPages} compiles them for production in any.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class ForgeEnvironment {

    /**
     * The system property {@link #useProduction} sets, which is the one a program that runs Forge in its
     * own JVM has back when Forge is closed, also after a start that failed.
     */
    public static final String PROPERTY = Environment.KEY

    /** The value {@link #useProduction} gives {@link #PROPERTY}. */
    public static final String PRODUCTION = Environment.PRODUCTION.getName()

    private ForgeEnvironment() {
    }

    /**
     * Makes production the Grails environment of this JVM, whatever environment it was started with.
     * An application of Forge calls this before Grails reads the environment.
     */
    static void useProduction() {
        System.setProperty(PROPERTY, PRODUCTION)
    }
}
