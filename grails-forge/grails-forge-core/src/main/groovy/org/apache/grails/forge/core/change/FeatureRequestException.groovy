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
package org.apache.grails.forge.core.change

import groovy.transform.CompileStatic
import org.grails.forge.feature.FeatureSelectionException

/**
 * The features that were asked to be added to a project cannot be added as they were asked for, for a
 * reason that adding features has and creating an application has not: a mistake of whoever asked,
 * as every {@link FeatureSelectionException} is. What the selection of the features refuses, as a
 * feature that does not exist, is a {@link FeatureSelectionException} of its own.
 *
 * @since 9.0
 */
@CompileStatic
class FeatureRequestException extends FeatureSelectionException {

    /** Two or more of the features that were asked for at once are of one exclusive group, of which an application has one. */
    public static final String CONTRADICTORY_REQUEST = 'contradictory-request'

    /** A feature would take the place of one that was asked for by name, and that was not said with {@code replace}. */
    public static final String REPLACE_REQUIRED = 'replace-required'

    /** A feature would take the place of one that a feature the application keeps or gets requires. */
    public static final String REQUIRED_BY_ANOTHER_FEATURE = 'required-by-another-feature'

    /** A feature was named by the coordinates of a plugin: a feature is added by its name, with the feature pack of the plugin given. */
    public static final String PLUGIN_COORDINATES = 'plugin-coordinates'

    /**
     * A preset was given to add its feature: an application is made from a preset when it is created.
     *
     * @since 9.0
     */
    public static final String PRESET = 'preset-not-added'

    private final String reason

    /**
     * @param message one line that says what is wrong and what to do about it
     * @param reason one of the codes of this class
     */
    FeatureRequestException(String message, String reason) {
        super(message)
        this.reason = reason
    }

    /**
     * @return why the features are not added, as one of the codes of this class, which stay the same
     * from one version to the next
     */
    String getReason() {
        return reason
    }
}
