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
package org.grails.forge.feature.github.workflows

import groovy.transform.CompileStatic

/**
 * GitHub secret.
 *
 * @author Pavol Gressa
 * @since 6.0.0
 */
@CompileStatic
class Secret {

    private final String name
    private final String value
    private final String description

    Secret(String name, String description) {
        this(name, null, description)
    }

    Secret(String name, String value, String description) {
        this.name = name
        this.value = value
        this.description = description
    }

    String getName() {
        return name
    }

    String getDescription() {
        return description
    }

    String getValue() {
        return value
    }
}
