/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.spring.aop.autoproxy;

import groovy.lang.GroovyObject;
import org.springframework.aop.framework.autoproxy.InfrastructureAdvisorAutoProxyCreator;

/**
 * Tells Spring always to proxy Groovy classes.
 *
 * @author Graeme Rocher
 * @since 1.2
 */
public class GroovyAwareInfrastructureAdvisorAutoProxyCreator extends InfrastructureAdvisorAutoProxyCreator {

    private static final long serialVersionUID = 5545896123964533688L;

    @Override
    protected boolean shouldProxyTargetClass(Class<?> beanClass, String beanName) {
        return GroovyObject.class.isAssignableFrom(beanClass) || super.shouldProxyTargetClass(beanClass, beanName);
    }
}
