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
package org.apache.grails.datastore.core.querytail

import groovy.transform.AutoClone
import groovy.transform.CompileStatic
import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy

import org.springframework.core.env.PropertyResolver

import org.grails.datastore.mapping.config.ConfigurationBuilder
import org.grails.datastore.mapping.config.Settings

/**
 * The settings of the {@code query-tail} capture: whether an application records the statements GORM
 * runs, SQL through Hibernate, Cypher through Neo4j and commands through MongoDB, and where. Capture
 * is off unless {@link #SETTING_ENABLED} is set, so an application started without it carries
 * nothing that records at all.
 *
 * @since 9.0
 */
@Builder(builderStrategy = SimpleStrategy, prefix = '')
@AutoClone
@CompileStatic
class QueryTailSettings {

    /** The configuration prefix of every query-tail setting, {@code grails.gorm.queryTail}. */
    public static final String PREFIX = Settings.PREFIX + '.queryTail'

    /** Whether statements are captured; {@code grails.gorm.queryTail.enabled}. */
    public static final String SETTING_ENABLED = PREFIX + '.enabled'

    /** The file statements are captured to; {@code grails.gorm.queryTail.file}. */
    public static final String SETTING_FILE = PREFIX + '.file'

    /** The system property the Grails Gradle plugin passes the build directory in, relative to the project. */
    public static final String TARGET_DIR_PROPERTY = 'grails.project.target.dir'

    /** The system property a build sets the build directory with by hand; it wins over the plugin's. */
    public static final String TARGET_DIR_OVERRIDE_PROPERTY = 'project.target.dir'

    /** The system property naming the application directory, which a relative file or build directory is joined to. */
    public static final String BASE_DIR_PROPERTY = 'base.dir'

    /** The default capture file, relative to the build directory. */
    public static final String DEFAULT_FILE = 'query-tail/capture.log'

    /** The build directory used when the Gradle plugin has not said where it is. */
    public static final String DEFAULT_TARGET_DIR = 'build'

    boolean enabled = false

    /**
     * The capture file, absolute or relative to the application directory, as the {@code query-tail}
     * command reads it. When unset the file is {@link #DEFAULT_FILE} under the build directory.
     */
    String file

    /**
     * Binds the query-tail settings alone, from {@link #PREFIX}, as the settings of a connection source bind them,
     * for a datastore that takes nothing else from {@code grails.gorm}, such as one around a client the application
     * built: a setting of {@code grails.gorm} that is not of query-tail is never read, so it cannot fail them.
     *
     * @param configuration the configuration
     * @return the settings
     */
    static QueryTailSettings of(PropertyResolver configuration) {
        new Binder(configuration).build()
    }

    /**
     * @return the capture file these settings name: an absolute one as it is, a relative one joined to
     * the application directory ({@code base.dir}) when that is known, else to the working directory
     */
    File resolveFile() {
        captureFile(file, applicationDir(), buildDir())
    }

    /**
     * The capture file an application writes when {@link #SETTING_FILE} is not set: {@link #DEFAULT_FILE} in the
     * build directory, resolved the way Grails resolves it everywhere else: {@code project.target.dir} when a
     * build sets it by hand, else the directory the Gradle plugin passes in {@code grails.project.target.dir},
     * else {@code build}; a relative one joined to the application directory ({@code base.dir}) when that is
     * known, else to the working directory.
     *
     * @return the capture file
     */
    static File defaultFile() {
        captureFile(null, applicationDir(), buildDir())
    }

    /**
     * The capture file an application writes, from its directories as whoever asks knows them, as the
     * {@code query-tail} command, which runs outside the application, knows them from its project: the
     * file {@link #SETTING_FILE} names, an absolute one as it is and a relative one joined to the
     * application directory, else {@link #DEFAULT_FILE} in the build directory.
     *
     * @param file the file {@link #SETTING_FILE} names, or null when it names none
     * @param applicationDir the application directory, or null when it is not known, which leaves a relative
     *     file relative to the working directory
     * @param buildDir the build directory
     * @return the capture file
     */
    static File captureFile(String file, File applicationDir, File buildDir) {
        file ? inApplication(file, applicationDir) : new File(buildDir, DEFAULT_FILE)
    }

    /** @return the application directory the system properties name, or null when they name none */
    private static File applicationDir() {
        String baseDir = System.getProperty(BASE_DIR_PROPERTY)
        baseDir ? new File(baseDir) : null
    }

    /** @return the build directory the system properties name, joined to the application directory */
    private static File buildDir() {
        inApplication(System.getProperty(TARGET_DIR_OVERRIDE_PROPERTY) ?: System.getProperty(TARGET_DIR_PROPERTY) ?: DEFAULT_TARGET_DIR,
                applicationDir())
    }

    /**
     * @return a path as it is when it is absolute, else joined to the application directory when that is
     * known, else relative to the working directory
     */
    private static File inApplication(String path, File applicationDir) {
        File file = new File(path)
        file.absolute || applicationDir == null ? file : new File(applicationDir, path)
    }

    /**
     * Binds the settings from {@link #PREFIX}.
     */
    @CompileStatic
    private static class Binder extends ConfigurationBuilder<QueryTailSettings, QueryTailSettings> {

        Binder(PropertyResolver propertyResolver) {
            super(propertyResolver, PREFIX)
        }

        @Override
        protected QueryTailSettings createBuilder() {
            new QueryTailSettings()
        }

        @Override
        protected QueryTailSettings toConfiguration(QueryTailSettings builder) {
            builder
        }
    }
}
