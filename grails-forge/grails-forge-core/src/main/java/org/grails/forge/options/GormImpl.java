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
package org.grails.forge.options;

import io.micronaut.core.annotation.NonNull;

public enum GormImpl {

    HIBERNATE("gorm-hibernate5"),
    MONGODB("gorm-mongodb"),
    NEO4J("gorm-neo4j");

    public static final GormImpl DEFAULT_OPTION = HIBERNATE;
    private final String featureName;

    GormImpl(String featureName) {
        this.featureName = featureName;
    }

    @NonNull
    public String getName() {
        return featureName;
    }

}
