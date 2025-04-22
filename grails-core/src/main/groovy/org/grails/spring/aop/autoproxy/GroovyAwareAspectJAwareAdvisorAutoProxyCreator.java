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
import org.springframework.aop.aspectj.annotation.AnnotationAwareAspectJAutoProxyCreator;
import org.springframework.aop.config.AopConfigUtils;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Enables AspectJ weaving from the application context.
 *
 * @author Graeme Rocher
 * @since 1.3.4
 */
public class GroovyAwareAspectJAwareAdvisorAutoProxyCreator extends AnnotationAwareAspectJAutoProxyCreator {



    private static final long serialVersionUID = 1;

    @Override
    protected boolean shouldProxyTargetClass(Class<?> beanClass, String beanName) {
        return GroovyObject.class.isAssignableFrom(beanClass) || super.shouldProxyTargetClass(beanClass, beanName);
    }
}
