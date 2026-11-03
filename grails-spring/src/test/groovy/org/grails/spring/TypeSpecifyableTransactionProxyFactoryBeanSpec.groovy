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
package org.grails.spring

import spock.lang.Specification

class TypeSpecifyableTransactionProxyFactoryBeanSpec extends Specification {

    void 'an explicit type is returned instead of asking the superclass'() {
        given:
        TypeSpecifyableTransactionProxyFactoryBean bean = new TypeSpecifyableTransactionProxyFactoryBean(String)

        expect:
        bean.objectType == String
    }

    void 'a null type falls back to the superclass determination'() {
        given:
        TypeSpecifyableTransactionProxyFactoryBean bean = new TypeSpecifyableTransactionProxyFactoryBean(null)

        expect:
        bean.objectType == null
    }

}
