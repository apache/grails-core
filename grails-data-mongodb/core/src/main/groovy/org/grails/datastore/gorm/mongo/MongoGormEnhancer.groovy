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
package org.grails.datastore.gorm.mongo

import groovy.transform.CompileStatic

import org.springframework.transaction.PlatformTransactionManager

import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.finders.DynamicFinder
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.mongo.connections.MongoConnectionSourceSettings

/**
 * GORM enhancer for Mongo.
 *
 * @author Graeme Rocher
 */
@CompileStatic
class MongoGormEnhancer extends GormEnhancer {

    MongoGormEnhancer(MongoDatastore datastore, PlatformTransactionManager transactionManager, boolean failOnError = false) {
        super(datastore, transactionManager, failOnError)
        registerMongoMethodExpressions()
    }

    MongoGormEnhancer(MongoDatastore datastore, PlatformTransactionManager transactionManager, MongoConnectionSourceSettings settings) {
        super(datastore, transactionManager, settings)
        registerMongoMethodExpressions()
    }

    static void registerMongoMethodExpressions() {
        DynamicFinder.registerNewMethodExpression(NearSphere)
        DynamicFinder.registerNewMethodExpression(Near)
        DynamicFinder.registerNewMethodExpression(WithinBox)
        DynamicFinder.registerNewMethodExpression(WithinPolygon)
        DynamicFinder.registerNewMethodExpression(WithinCircle)
        DynamicFinder.registerNewMethodExpression(GeoWithin)
        DynamicFinder.registerNewMethodExpression(GeoIntersects)
    }

    MongoGormEnhancer(MongoDatastore datastore) {
        this(datastore, null)
    }

}
