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
package org.grails.datastore.mapping.keyvalue.mapping.config

import groovy.transform.CompileStatic
import groovy.transform.builder.Builder
import groovy.transform.builder.SimpleStrategy
import org.grails.datastore.mapping.config.Entity
import org.grails.datastore.mapping.config.Property

/**
 * <p>A Family is a grouping of KeyValue pairs and is typically composed
 * of a keyspace and the family name.</p>
 *
 * <p>For example in Cassandra a Family relates to a ColumnFamily or in BigTable
 * a Family relates to a an Entity kind.</p>
 *
 * <p>Other more simplistic key/value stores may just use prefixes on keys for the family</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
@Builder(builderStrategy = SimpleStrategy, prefix = '')
class Family extends Entity<KeyValue> {

    String keyspace
    String family

    Family() {
    }

    Family(String keyspace, String family) {
        this.keyspace = keyspace
        this.family = family
    }

    @Override
    protected KeyValue newProperty() {
        return new KeyValue()
    }
}
