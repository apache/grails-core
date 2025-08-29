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

package org.grails.datastore.gorm.jdbc.schema

import java.sql.Connection

import javax.sql.DataSource

/**
 * A resolver that helps resolve information about the database schema. Required by multi tenancy support
 *
 * @author Graeme Rocher
 * @since 6.0
 */
interface SchemaHandler {

    /**
     * @return Resolves the schema names
     */
    Collection<String> resolveSchemaNames(DataSource dataSource)

    /**
     * Uses the given schema. Defaults to "SET SCHEMA %s"
     */
    void useSchema(Connection connection, String name)

    /**
     * Uses the given schema. Defaults to "SET SCHEMA PUBLIC"
     */
    void useDefaultSchema(Connection connection)

    /**
     * Creates the given schema. Defaults to "CREATE SCHEMA %s"
     */
    void createSchema(Connection connection, String name)
}
