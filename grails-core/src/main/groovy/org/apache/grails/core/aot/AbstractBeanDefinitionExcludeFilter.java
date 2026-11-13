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
package org.apache.grails.core.aot;

import org.springframework.beans.factory.aot.BeanRegistrationExcludeFilter;
import org.springframework.beans.factory.support.RegisteredBean;

/**
 * Keeps abstract bean definitions out of ahead-of-time processing.
 *
 * <p>An abstract definition is a template: it carries property values for children to inherit
 * and is never instantiated. Spring's bean-definition code generator has no representation for
 * that — it emits neither the abstract flag nor a bean class, so the definition is regenerated
 * as a concrete bean of type {@code Object} carrying the template's properties, which fails as
 * soon as the context applies them.</p>
 *
 * <p>Children are unaffected: ahead-of-time processing generates them from their <em>merged</em>
 * definition, so inherited values are already folded in and no parent is needed at runtime.
 * A definition contributed dynamically still finds its parent, because the post-processor that
 * registers the template runs during refresh in an ahead-of-time context as it does in any other.</p>
 *
 * @since 8.0
 * @see org.grails.spring.beans.AbstractResourceLocatorPostProcessor
 */
public class AbstractBeanDefinitionExcludeFilter implements BeanRegistrationExcludeFilter {

    @Override
    public boolean isExcludedFromAotProcessing(RegisteredBean registeredBean) {
        return registeredBean.getMergedBeanDefinition().isAbstract();
    }

}
