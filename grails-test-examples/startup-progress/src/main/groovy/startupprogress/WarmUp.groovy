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
package startupprogress

import java.time.Duration

import groovy.transform.CompileStatic

import org.springframework.beans.factory.BeanNameAware
import org.springframework.beans.factory.InitializingBean
import org.springframework.context.EnvironmentAware
import org.springframework.core.env.Environment

/**
 * A bean that takes {@code startup.demo.delay.<bean name>} to create, standing in for the beans that make
 * real applications slow to start, and fails when {@code startup.demo.fail} is {@code bean}.
 */
@CompileStatic
class WarmUp implements BeanNameAware, EnvironmentAware, InitializingBean {

    String beanName

    Environment environment

    @Override
    void afterPropertiesSet() {
        Thread.sleep(environment.getProperty("startup.demo.delay.${beanName}", Duration, Duration.ZERO).toMillis())
        if (beanName == 'searchIndex' && environment.getProperty('startup.demo.fail') == 'bean') {
            throw new IllegalStateException('Could not open the search index: /var/lib/search is not writable')
        }
    }
}
