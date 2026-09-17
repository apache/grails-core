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
package grails.spring

import groovy.transform.CompileStatic
import org.springframework.context.ApplicationContext

import org.grails.spring.RuntimeSpringConfiguration
import org.grails.web.servlet.context.support.WebRuntimeSpringConfiguration

/**
 * Extended version of the BeanBuilder class that provides support for constructing WebApplicationContext instances
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class WebBeanBuilder extends BeanBuilder {

    WebBeanBuilder() {
        super()
    }

    WebBeanBuilder(ClassLoader classLoader) {
        super(classLoader)
    }

    WebBeanBuilder(ApplicationContext parent) {
        super(parent)
    }

    WebBeanBuilder(ApplicationContext parent, ClassLoader classLoader) {
        super(parent, classLoader)
    }

    @Override
    protected RuntimeSpringConfiguration createRuntimeSpringConfiguration(ApplicationContext parent, ClassLoader classLoader) {
        return new WebRuntimeSpringConfiguration(parent, classLoader)
    }

}
