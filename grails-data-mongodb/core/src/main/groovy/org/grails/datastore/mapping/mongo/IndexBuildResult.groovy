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

/**
 * What an index build applied, as its summary line reports it. The counts are of index declarations, so
 * declaring the same keys twice can count one created and one already present.
 *
 * @param database       the database the build was for
 * @param domainClasses  the domain classes considered, including those that declare no indexes
 * @param created        declarations whose index did not exist before the build
 * @param recreated      indexes dropped and built again for {@code recreateOnConflict}
 * @param alreadyPresent declarations whose index already existed, including those whose options were updated
 *                       in place
 * @param unclassified   declarations applied on a collection whose existing indexes could not be listed, so
 *                       neither created nor already present
 * @param failures       declarations that could not be applied; each is logged as it fails
 * @param elapsedMillis  how long the build took
 * @see MongoDatastore#buildIndexAsync()
 */
@CompileStatic
@SuppressWarnings(['ClassStartsWithBlankLine', 'Indentation'])
record IndexBuildResult(String database, int domainClasses, int created, int recreated, int alreadyPresent,
                               int unclassified, int failures, long elapsedMillis) {

}
