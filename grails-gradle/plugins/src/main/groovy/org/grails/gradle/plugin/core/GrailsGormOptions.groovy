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

import javax.inject.Inject

import groovy.transform.CompileStatic

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property

import grails.util.BuildSettings

/**
 * GORM compilation options, configured through the nested {@code grails { gorm { } }} block:
 *
 * <pre>
 * grails {
 *     gorm {
 *         defaultIdType = 'native'
 *     }
 * }
 * </pre>
 *
 * @since 8.0
 */
@CompileStatic
class GrailsGormOptions implements Serializable {

    private static final long serialVersionUID = 0L

    /**
     * The type of the {@code id} GORM adds to a domain class that declares none of its own.
     *
     * <p>{@code 'long'}, the default, gives every domain class a {@code Long} id, as every earlier
     * Grails release did. {@code 'native'} gives it the identity type of the GORM implementation it is
     * mapped with: a MongoDB domain class is given a {@code String} id, holding the hexadecimal form of
     * a generated {@code ObjectId}, while a Hibernate one keeps {@code Long}.</p>
     *
     * <p>This is resolved when the domain class is compiled, from its {@code mapWith} property and the
     * GORM implementations on the compilation classpath, so an application using more than one
     * implementation gets the right type for each domain class from the single setting. A domain class
     * that declares an {@code id} keeps the type it declares either way.</p>
     *
     * <p>Changing this changes the type of a column or field that already holds data. It is a setting
     * to choose when an application is written, not one to turn on later without migrating what has
     * already been stored.</p>
     */
    final Property<String> defaultIdType

    @Inject
    GrailsGormOptions(ObjectFactory objects) {
        this.defaultIdType = objects.property(String).convention(BuildSettings.GORM_DEFAULT_ID_TYPE_LONG)
    }
}
