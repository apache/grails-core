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
package org.grails.gradle.plugin.core

import groovy.transform.CompileStatic

import org.gradle.api.InvalidUserDataException
import org.gradle.api.tasks.Input
import org.gradle.process.CommandLineArgumentProvider

import grails.util.BuildSettings

/**
 * Publishes the nested {@code grails { gorm { defaultIdType } }} setting (see
 * {@link BuildSettings#GORM_DEFAULT_ID_TYPE}) to the Groovy compiler's worker JVM as a system property,
 * so that GORM's entity transformation can decide the type of the {@code id} it adds to a domain class
 * that declares none.
 *
 * <p>The lazy {@link GrailsGormOptions} property is read in {@link #asArguments} (at compile time, not
 * configuration time). The effective value is also exposed as an {@link Input} getter so that changing
 * it invalidates the compile task: the identity type is compiled into the domain class, and a stale
 * class file would otherwise keep the type the previous setting asked for.</p>
 *
 * @since 8.0
 */
@CompileStatic
class GrailsGormIdTypeProvider implements CommandLineArgumentProvider {

    private final GrailsGormOptions gorm

    GrailsGormIdTypeProvider(GrailsGormOptions gorm) {
        this.gorm = gorm
    }

    /**
     * The configured identity type, validated. Stating a value that GORM does not recognise fails the
     * build here, where the setting is, rather than silently compiling every domain class with a
     * {@code Long} id.
     */
    @Input
    String getDefaultIdType() {
        String value = gorm.defaultIdType.getOrElse(BuildSettings.GORM_DEFAULT_ID_TYPE_LONG).toLowerCase(Locale.ROOT)
        if (!(value in [BuildSettings.GORM_DEFAULT_ID_TYPE_LONG, BuildSettings.GORM_DEFAULT_ID_TYPE_NATIVE])) {
            throw new InvalidUserDataException("Invalid value [$value] for grails { gorm { defaultIdType } }. " +
                    "Expected '$BuildSettings.GORM_DEFAULT_ID_TYPE_LONG' or '$BuildSettings.GORM_DEFAULT_ID_TYPE_NATIVE'.")
        }
        value
    }

    @Override
    Iterable<String> asArguments() {
        String value = getDefaultIdType()
        if (value == BuildSettings.GORM_DEFAULT_ID_TYPE_LONG) {
            // The compiler's own default. Left unstated so that an application that has not asked for
            // anything passes no argument, and its compiler workers stay shared with every other one.
            return []
        }
        ["-D${BuildSettings.GORM_DEFAULT_ID_TYPE}=$value".toString()]
    }
}
