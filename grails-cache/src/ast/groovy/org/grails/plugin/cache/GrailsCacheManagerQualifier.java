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
package org.grails.plugin.cache;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.beans.factory.annotation.Qualifier;

/**
 * Qualifies the field {@link GrailsCacheManagerAware} has its cache manager injected into, so that Spring injects
 * the bean named {@code grailsCacheManager}, or the bean with that alias, and no other.
 *
 * <p>Spring matches a qualifier annotation to a bean's name or alias by its {@code value}, and also to a bean that
 * declares the same qualifier annotation itself: on its class, on its {@code @Bean} method, or on its bean
 * definition. A bean that declares Spring's {@code @Qualifier("grailsCacheManager")} would so match a field
 * qualified with that annotation, and be taken for it where it is marked primary. This annotation can only be put
 * on fields, so no class or {@code @Bean} method can declare it, and a bean matches it only by its name or alias.
 * Spring's own {@link Qualifier}, which marks this annotation as a qualifier, has no value, so it matches no bean by
 * name either.</p>
 *
 * @since 9.0
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Qualifier
public @interface GrailsCacheManagerQualifier {

    /**
     * @return the name, or alias, of the bean the field is injected with
     */
    String value() default "grailsCacheManager";
}
