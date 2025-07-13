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

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.runtime.JdbcOperations;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.transaction.annotation.ReadOnly;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@JdbcRepository(dialect = Dialect.POSTGRES)
public abstract class FeatureRepository implements CrudRepository<Feature, Long> {

    private final JdbcOperations jdbcOperations;

    public FeatureRepository(JdbcOperations jdbcOperations) {
        this.jdbcOperations = jdbcOperations;
    }

    @ReadOnly
    List<TotalDTO> topFeatures() {
        return this.jdbcOperations
                .prepareStatement(query("name", "feature"),
                        statement -> {
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSetToTotals(resultSet);
            }
        });
    }

    @ReadOnly
    List<TotalDTO> topGorm() {
        return this.jdbcOperations
                .prepareStatement(query("gorm", "application"),
                        statement -> {
                            try (ResultSet resultSet = statement.executeQuery()) {
                                return resultSetToTotals(resultSet);
                            }
                        });
    }

    @ReadOnly
    List<TotalDTO> topBuildTools() {
        return this.jdbcOperations
                .prepareStatement(query("build_tool", "application"),
                        statement -> {
                            try (ResultSet resultSet = statement.executeQuery()) {
                                return resultSetToTotals(resultSet);
                            }
                        });
    }

    @ReadOnly
    List<TotalDTO> topTestFrameworks() {
        return this.jdbcOperations
                .prepareStatement(query("test_framework", "application"),
                        statement -> {
                            try (ResultSet resultSet = statement.executeQuery()) {
                                return resultSetToTotals(resultSet);
                            }
                        });
    }

    @ReadOnly
    List<TotalDTO> topJdkVersion() {
        return this.jdbcOperations
                .prepareStatement(query("jdk_version", "application"),
                        statement -> {
                            try (ResultSet resultSet = statement.executeQuery()) {
                                return resultSetToTotals(resultSet);
                            }
                        });
    }

    private List<TotalDTO> resultSetToTotals(ResultSet resultSet) throws SQLException {
        List<TotalDTO> results = new ArrayList<>(40);
        while (resultSet.next()) {
            results.add(
                    new TotalDTO(
                        resultSet.getString("name"),
                        resultSet.getLong("total")
                    )
            );
        }
        return results;
    }

    private String query(String name, String table) {
        return "SELECT " + name + " AS name, count(*) AS total FROM " + table + " GROUP BY name ORDER BY total";
    }
}
