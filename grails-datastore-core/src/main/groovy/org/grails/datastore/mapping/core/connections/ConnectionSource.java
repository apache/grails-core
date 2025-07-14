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
package org.grails.datastore.mapping.core.connections;

import java.io.Closeable;

/**
 * Represents a connection source, which could be a SQL DataSource, a MongoClient etc.
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public interface ConnectionSource<T, S extends ConnectionSourceSettings> extends Closeable {

    /**
     * The name of the default connection source
     */
    String DEFAULT = "DEFAULT";

    /**
     * Constance for a mapping to all connection sources
     */
    String ALL = "ALL";

    /**
     * @return The name of the connection source
     */
    String getName();

    /**
     * @return The underlying native connection source, for example a SQL {@link javax.sql.DataSource}
     */
    T getSource();

    /**
     * @return The settings for the {@link ConnectionSource}
     */
    S getSettings();

}
