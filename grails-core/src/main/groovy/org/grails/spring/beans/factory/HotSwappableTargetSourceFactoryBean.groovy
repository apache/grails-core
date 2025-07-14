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
package org.grails.spring.beans.factory

import groovy.transform.CompileStatic
import org.springframework.aop.target.HotSwappableTargetSource
import org.springframework.beans.factory.FactoryBean
import org.springframework.beans.factory.InitializingBean

/**
 * Creates a HotSwappableTargetSource
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class HotSwappableTargetSourceFactoryBean implements FactoryBean<HotSwappableTargetSource>, InitializingBean {

    protected HotSwappableTargetSource targetSource
    protected Object target

    @Override
    HotSwappableTargetSource getObject() throws Exception {
        return targetSource
    }

    @Override
    Class<?> getObjectType() { HotSwappableTargetSource }

    @Override
    boolean isSingleton() {
        return true
    }

    void setTarget(Object object) {
        this.target = object
    }

    @Override
    void afterPropertiesSet() throws Exception {
        this.targetSource = new HotSwappableTargetSource(this.target)
    }
}
