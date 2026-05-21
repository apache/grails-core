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

import org.grails.datastore.mapping.mongo.config.MongoMappingContext
import org.junit.Before
import org.springframework.context.support.GenericApplicationContext

abstract class AbstractMongoTest {

    protected MongoDatastore md

    @Before
    void setUp() {
        md = new MongoDatastore(new MongoMappingContext(getClass().simpleName))
        def ctx = new GenericApplicationContext()
        ctx.refresh()
        md.applicationContext = ctx
        md.afterPropertiesSet()
    }
}
