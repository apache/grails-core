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
package org.grails.forge.cli;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.TypeHint;
import picocli.CommandLine;

import java.util.Optional;

/**
 * Picocli factory implementation that uses a Micronaut BeanContext to obtain bean instances.
 */
@TypeHint(typeNames = {
    "picocli.CommandLine$AutoHelpMixin",
    "picocli.CommandLine$Model$CommandSpec"
}, accessType = {TypeHint.AccessType.ALL_DECLARED_CONSTRUCTORS, TypeHint.AccessType.ALL_DECLARED_FIELDS})
class GrailsPicocliFactory implements CommandLine.IFactory {

    private final CommandLine.IFactory defaultFactory = CommandLine.defaultFactory();
    private final BeanContext beanContext;

    public GrailsPicocliFactory() {
        this(ApplicationContext.run());
    }

    public GrailsPicocliFactory(BeanContext beanContext) {
        this.beanContext = beanContext;
    }

    @Override
    public <K> K create(Class<K> cls) throws Exception {
        Optional<K> bean = beanContext.findOrInstantiateBean(cls);
        if (bean.isPresent()) {
            return bean.get();
        } else {
            return defaultFactory.create(cls);
        }
    }
}
