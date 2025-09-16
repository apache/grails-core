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

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import grails.artefact.Artefact
import grails.gorm.transactions.ReadOnly
import grails.rest.RestfulController
import org.grails.datastore.gorm.GormEntity

@Artefact('Controller')
@ReadOnly
@CompileStatic
class RestfulServiceController<T extends GormEntity<T>> extends RestfulController<T> {

    RestfulServiceController(Class<T> resource, boolean readOnly) {
        super(resource, readOnly)
    }

    protected GormService<T> getService() {
        DomainServiceLocator.<T>resolve(resource)
    }

    @Override
    protected T queryForResource(Serializable id) {
        getService().get(id)
    }

    @Override
    protected List<T> listAllResources(Map params) {
        getService().list(params)
    }

    @Override
    @CompileDynamic
    protected Integer countResources() {
        getService().count(params)
    }

    @Override
    protected T saveResource(T resource) {
        getService().save(resource)
    }

    @Override
    protected T updateResource(T resource) {
        getService().save(resource)
    }

    @Override
    protected void deleteResource(T resource) {
        getService().delete(resource.ident())
    }
}
