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

package grails.gorm.async

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.gorm.async.GormAsyncStaticApi

/**
 * Adds Grails Async features to an entity that implements this trait, including the ability to run GORM tasks in a separate thread
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
trait AsyncEntity<D> extends GormEntity<D> {

    /**
     * @return The async version of the GORM static API
     */
    static GormAsyncStaticApi<D> getAsync() {
        return new GormAsyncStaticApi(GormEnhancer.findStaticApi(this))
    }
}