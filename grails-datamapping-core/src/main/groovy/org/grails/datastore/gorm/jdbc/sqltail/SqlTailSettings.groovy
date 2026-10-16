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
package org.grails.datastore.gorm.jdbc.sqltail

import groovy.transform.CompileStatic

import org.springframework.core.env.PropertyResolver

import org.grails.datastore.mapping.config.Settings

/**
 * The settings of the {@code sql-tail} capture: whether an application records the SQL statements
 * Hibernate executes, and where. Capture is off unless {@link #SETTING_ENABLED} is set, so an
 * application started without it carries no statement inspector at all.
 *
 * @since 8.1
 */
@CompileStatic
class SqlTailSettings {

    /** The configuration prefix of every sql-tail setting, {@code grails.gorm.sqlTail}. */
    public static final String PREFIX = Settings.PREFIX + '.sqlTail'

    /** Whether statements are captured; {@code grails.gorm.sqlTail.enabled}. */
    public static final String SETTING_ENABLED = PREFIX + '.enabled'

    /** The file statements are captured to; {@code grails.gorm.sqlTail.file}. */
    public static final String SETTING_FILE = PREFIX + '.file'

    /** The system property the Grails Gradle plugin passes the build directory in, relative to the project. */
    public static final String TARGET_DIR_PROPERTY = 'grails.project.target.dir'

    /** The system property a build sets the build directory with by hand; it wins over the plugin's. */
    public static final String TARGET_DIR_OVERRIDE_PROPERTY = 'project.target.dir'

    /** The system property naming the application directory, which a relative build directory is joined to. */
    public static final String BASE_DIR_PROPERTY = 'base.dir'

    /** The default capture file, relative to the build directory. */
    public static final String DEFAULT_FILE = 'sql-tail/capture.log'

    /** The build directory used when the Gradle plugin has not said where it is. */
    public static final String DEFAULT_TARGET_DIR = 'build'

    boolean enabled = false

    /**
     * The capture file, absolute or relative to the working directory. When unset the file is
     * {@link #DEFAULT_FILE} under the build directory.
     */
    String file

    /**
     * Reads the sql-tail settings of a configuration.
     *
     * @param configuration the application configuration
     * @return the settings, disabled when the configuration says nothing
     */
    static SqlTailSettings from(PropertyResolver configuration) {
        SqlTailSettings settings = new SqlTailSettings()
        if (configuration != null) {
            settings.enabled = configuration.getProperty(SETTING_ENABLED, Boolean, false)
            settings.file = configuration.getProperty(SETTING_FILE, String, null)
        }
        settings
    }

    /**
     * @return the capture file these settings name, resolved against the working directory
     */
    File resolveFile() {
        file ? new File(file) : defaultFile()
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
        String targetDir = System.getProperty(TARGET_DIR_OVERRIDE_PROPERTY) ?: System.getProperty(TARGET_DIR_PROPERTY) ?: DEFAULT_TARGET_DIR
        File directory = new File(targetDir)
        if (!directory.absolute) {
            String baseDir = System.getProperty(BASE_DIR_PROPERTY)
            directory = baseDir ? new File(baseDir, targetDir) : directory
        }
        new File(directory, DEFAULT_FILE)
    }
}
