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
package org.grails.datastore.mapping.mongo

import groovy.transform.CompileStatic
import org.bson.Document

/**
 * An index on a collection that domain classes map, whose keys none of those domain classes declares.
 *
 * @param database   the database the collection is in
 * @param collection the collection the index is on
 * @param name       the name of the index
 * @param key        the index's key pattern, as {@code listIndexes} reports it
 * @param definition the whole index description {@code listIndexes} reports, options included
 * @see MongoDatastore#findUndeclaredIndexes()
 * @see MongoDatastore#dropUndeclaredIndexes()
 */
@CompileStatic
@SuppressWarnings(['ClassStartsWithBlankLine', 'Indentation'])
record UndeclaredIndex(String database, String collection, String name, Document key, Document definition) {

}
