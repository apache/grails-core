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
package org.grails.datastore.gorm.neo4j.proxy;

import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.engine.AssociationQueryExecutor;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.proxy.AssociationQueryProxyHandler;

import java.io.Serializable;

public class Neo4jAssociationQueryProxyHandler extends AssociationQueryProxyHandler {

    public Neo4jAssociationQueryProxyHandler(Session session, AssociationQueryExecutor executor, Serializable associationKey) {
        super(session, executor, associationKey);
    }

    @Override
    protected Object invokeEntityProxyMethods(Object self, String methodName, Object[] args) {
        if (methodName.equals(GET_ID_METHOD)) {
            return INVOKE_IMPLEMENTATION;
        } else {
            return super.invokeEntityProxyMethods(self, methodName, args);
        }
    }


    @Override
    protected Object getPropertyBeforeResolving(Object self, String property) {
        if (property.equals(GormProperties.IDENTITY)) {
            return INVOKE_IMPLEMENTATION;
        } else {
            return super.getPropertyBeforeResolving(self, property);
        }
    }
}
