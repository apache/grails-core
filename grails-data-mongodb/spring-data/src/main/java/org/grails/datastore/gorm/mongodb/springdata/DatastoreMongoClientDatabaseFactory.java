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
package org.grails.datastore.gorm.mongodb.springdata;

import com.mongodb.client.MongoClient;

import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

import org.grails.datastore.mapping.mongo.MongoDatastore;

/**
 * A {@link SimpleMongoClientDatabaseFactory} over the client a {@link MongoDatastore} has now, rather than the one
 * it had when the factory was created: the datastore replaces its client when it is started again after a CRaC
 * restore. The client stays the datastore's to close.
 *
 * <p>Java rather than Groovy: the factory's hierarchy declares {@code getMongoCluster()} with two return types, and
 * only javac bridges an override to both.
 */
final class DatastoreMongoClientDatabaseFactory extends SimpleMongoClientDatabaseFactory {

    private final MongoDatastore datastore;

    DatastoreMongoClientDatabaseFactory(MongoDatastore datastore) {
        // This constructor does not take ownership of the client, so destroy() leaves it to GORM.
        super(datastore.getMongoClient(), datastore.getDefaultDatabase());
        this.datastore = datastore;
    }

    /**
     * Every use the factory makes of the client goes through here.
     */
    @Override
    public MongoClient getMongoCluster() {
        return datastore.getMongoClient();
    }
}
