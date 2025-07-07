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
package org.grails.forge.analytics.postgres;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;

import java.util.Objects;

/**
 * Models a selected application feature.
 *
 * @author graemerocher
 * @since 6.0.0
 */
@MappedEntity
public class Feature {
    @Id
    @GeneratedValue
    private Long id;
    @Relation(value = Relation.Kind.MANY_TO_ONE)
    private final Application application;
    private final String name;

    public Feature(
            @NonNull Application application, @NonNull String name) {
        this.application = Objects.requireNonNull(application, "Application cannot be null");
        this.name = Objects.requireNonNull(name, "Feature cannot be null");
    }

    /**
     * @return The application
     */
    public @NonNull Application getApplication() {
        return application;
    }

    /**
     * @return The feature name
     */
    public @NonNull String getName() {
        return name;
    }

    /**
     * The ID.
     * @return The id
     */
    public @Nullable Long getId() {
        return id;
    }

    /**
     * @param id The ID
     */
    public void setId(@Nullable Long id) {
        this.id = id;
    }
}
