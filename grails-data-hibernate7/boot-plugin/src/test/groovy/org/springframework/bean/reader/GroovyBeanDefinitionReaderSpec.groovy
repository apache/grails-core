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
package org.springframework.bean.reader

import org.springframework.beans.factory.InitializingBean
import org.springframework.beans.factory.groovy.GroovyBeanDefinitionReader
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spock.lang.Specification

/**
 * Created by graemerocher on 06/02/14.
 */
class GroovyBeanDefinitionReaderSpec extends Specification{

    protected AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

    void setup() {
        MyBean.blah = 'foo'
    }

    void "Test singletons are pre-instantiated with beans added by GroovyBeanDefinitionReader"() {
        when:"The groovy reader is used"
            def beanReader= new GroovyBeanDefinitionReader(context)
            beanReader.beans {
                myBean(MyBean)
            }

            context.refresh()

        then:"The bean is pre instantiated"
            MyBean.blah == 'created'
    }
}
class MyBean implements InitializingBean{
    static String blah = 'foo'

    @Override
    void afterPropertiesSet() throws Exception {
        blah = 'created'
    }
}
