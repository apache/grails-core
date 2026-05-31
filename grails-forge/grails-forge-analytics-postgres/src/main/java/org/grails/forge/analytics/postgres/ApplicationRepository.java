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

import java.util.List;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.PageableRepository;

/**
 * Repository for storing generated applications.
 *
 * @author graemerocher
 * @since 6.0.0
 */
@JdbcRepository(dialect = Dialect.POSTGRES)
public abstract class ApplicationRepository implements PageableRepository<Application, Long> {
    /**
     * List the applications with the features.
     * @param pageable The pageable
     * @return The application and the features
     */
    @Join("features")
    public abstract List<Application> list(Pageable pageable);

    public abstract void deleteById(@NonNull @Id Long id);

    @Override
    public void delete(@NonNull Application application) {
        deleteById(application.getId());
    }
}
