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

import javassist.util.proxy.MethodHandler;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.engine.AssociationQueryExecutor;
import org.grails.datastore.mapping.proxy.JavassistProxyFactory;

import java.io.Serializable;

/**
 * extends {@link org.grails.datastore.mapping.proxy.JavassistProxyFactory} to capture method calls `hashCode` and `equals`
 * without expanding the proxy
 */
public class Neo4jProxyFactory extends JavassistProxyFactory {

    protected <K extends Serializable, T> MethodHandler createMethodHandler(Session session, AssociationQueryExecutor<K, T> executor, K associationKey) {
        return new Neo4jAssociationQueryProxyHandler(session, executor, associationKey);
    }
}
