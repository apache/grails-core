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

import grails.artefact.Artefact
import grails.gorm.transactions.ReadOnly
import grails.rest.RestfulController
import grails.util.Holders
import org.grails.datastore.gorm.GormEntityApi

@Artefact("Controller")
@ReadOnly
class RestfulServiceController<T> extends RestfulController<T> {

    RestfulServiceController(Class<T> resource, boolean readOnly) {
        super(resource, readOnly)
    }

    protected def getService() {
        Holders.grailsApplication.getMainContext().getBean(resourceName + 'Service')
    }

    protected T queryForResource(Serializable id) {
        getService().get(id)
    }

    protected List<T> listAllResources(Map params) {
        getService().list(params)
    }

    protected Integer countResources() {
        getService().count()
    }

    protected T saveResource(T resource) {
        getService().save(resource)
    }

    protected T updateResource(T resource) {
        getService().save(resource)
    }

    protected void deleteResource(T resource) {
        getService().delete(((GormEntityApi) resource).ident())
    }
}