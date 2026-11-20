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
package org.grails.forge.options

import groovy.transform.CompileStatic

/**
 * The options of a generation that choose a feature: an option that was given is a choice of whoever
 * asked for the application, and one that was not given has a default, which gives way to the features.
 *
 * @since 9.0
 * @see Options#withGiven(java.util.Collection)
 */
@CompileStatic
enum OptionName {

    DATA('Grails Data implementation', null),
    SERVLET('servlet container', 'embedded-servlet'),
    RELOADING('development reloading', 'reloading')

    private final String label

    private final String group

    OptionName(String label, String group) {
        this.label = label
        this.group = group
    }

    /**
     * @return the exclusive group of the features the option chooses among, or {@code null} for an option
     * whose features are in no one group
     */
    String getGroup() {
        return group
    }

    /**
     * @return what the option chooses, as a message says it
     */
    String getLabel() {
        return label
    }
}
