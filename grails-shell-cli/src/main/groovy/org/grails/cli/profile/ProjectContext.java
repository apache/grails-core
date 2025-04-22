/*
 * Copyright 2014 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.cli.profile;

import grails.build.logging.GrailsConsole;
import grails.config.ConfigMap;

import java.io.File;

/**
 * The project context used by a {@link org.grails.cli.profile.Profile}
 *
 * @author Lari Hotari
 * @author Graeme Rocher
 */
public interface ProjectContext {
    /**
     * @return The {@link grails.build.logging.GrailsConsole} instance
     */
    GrailsConsole getConsole();

    /**
     *
     * @return The base directory of the project
     */
    File getBaseDir();

    /**
     * @return The codegen config
     */
    ConfigMap getConfig();

    /**
     * Obtains a value from the codegen configuration
     *
     * @param path The path to value
     * @return The value or null if not set
     */
    String navigateConfig(String... path);

    /**
     * Obtains a value of the given type from the codegen configuration
     *
     * @param requiredType The required return type
     * @param path The path to value
     * @return The value or null if not set
     */
    <T> T navigateConfigForType(Class<T> requiredType, String... path);
}
