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

package grails.plugin.scaffolding

import groovy.transform.CompileStatic

import grails.artefact.Artefact
import grails.gorm.api.GormAllOperations
import grails.gorm.transactions.ReadOnly
import grails.gorm.transactions.Transactional
import grails.util.GrailsNameUtils
import org.grails.datastore.gorm.GormEntity
import org.grails.datastore.gorm.GormEntityApi
import org.grails.datastore.gorm.GormRegistry

@Artefact('Service')
@ReadOnly
@CompileStatic
class GormService<T extends GormEntity<T>> implements ScaffoldService<T, Serializable> {

    @Lazy
    GormAllOperations<T> gormStaticApi = GormRegistry.findStaticApi(resource) as GormAllOperations<T>
    Class<T> resource
    String resourceName
    String resourceClassName
    boolean readOnly

    GormService(Class<T> resource, boolean readOnly) {
        this.resource = resource
        this.readOnly = readOnly
        resourceClassName = resource.simpleName
        resourceName = GrailsNameUtils.getPropertyName(resource)
    }

    @Override
    T get(Serializable id) {
        gormStaticApi.get(id)
    }

    @Override
    List<T> list(Map args) {
        gormStaticApi.list(args)
    }

    @Override
    Long count(Map args) {
        gormStaticApi.count()
    }

    @Override
    @Transactional
    void delete(Serializable id) {
        if (readOnly) {
            return
        }
        ((GormEntityApi) get(id)).delete(flush: true)
    }

    @Override
    @Transactional
    T save(T instance) {
        if (readOnly) {
            return instance
        }
        (T) ((GormEntityApi) instance).save(flush: true)
    }
}
