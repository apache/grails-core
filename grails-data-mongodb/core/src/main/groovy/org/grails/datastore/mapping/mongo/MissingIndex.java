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
package org.grails.datastore.mapping.mongo;

import org.bson.Document;

/**
 * An index a domain class declares that its collection does not have.
 *
 * @param database    the database the collection is in
 * @param collection  the collection the index belongs on
 * @param domainClass the name of the domain class declaring it, the first of them if several do
 * @param key         the declared key pattern
 * @param options     the options declared with it, such as {@code unique} or {@code expireAfterSeconds}
 * @see MongoDatastore#findMissingIndexes()
 */
public record MissingIndex(String database, String collection, String domainClass, Document key, Document options) {
}
