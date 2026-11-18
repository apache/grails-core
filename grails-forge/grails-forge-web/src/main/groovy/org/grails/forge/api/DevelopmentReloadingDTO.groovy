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
package org.grails.forge.api

import groovy.transform.CompileStatic
import org.springframework.context.MessageSource
import jakarta.annotation.Nonnull
import org.grails.forge.options.DevelopmentReloading
import org.grails.forge.util.NameUtils

/**
 * DTO objects for {@link DevelopmentReloading}.
 */
@CompileStatic
class DevelopmentReloadingDTO extends Linkable implements Selectable<DevelopmentReloading> {

    static final String MESSAGE_PREFIX = GrailsForgeConfiguration.PREFIX + '.developmentReloading.'
    private final String name
    private final String description
    private final DevelopmentReloading value

    /**
     * @param developmentReloading The developmentReloading
     */
    DevelopmentReloadingDTO(DevelopmentReloading developmentReloading) {
        this.value = developmentReloading
        this.name = developmentReloading.toString()
        this.description = developmentReloading.name()
    }

    /**
     * @param name the name
     * @param description The description
     */
    DevelopmentReloadingDTO(DevelopmentReloading value, String name, String description) {
        this.value = value
        this.name = name
        this.description = description
    }

    /**
     * i18n constructor.
     * @param developmentReloading The type
     * @param messageSource The message source
     * @param locale The locale
     */
    DevelopmentReloadingDTO(DevelopmentReloading developmentReloading, MessageSource messageSource, Locale locale) {
        this.value = developmentReloading
        this.name = developmentReloading.toString()
        this.description = ForgeMessages.message(messageSource, locale, MESSAGE_PREFIX + name + '.description', NameUtils.getNaturalNameOfEnum(name))
    }

    @Nonnull
    @Override
        String getDescription() {
        return description
    }

    @Nonnull
    String getName() {
        return name
    }

    @Override
        @Nonnull
    DevelopmentReloading getValue() {
        return value
    }

    @Override
        String getLabel() {
        return value.getLabel()
    }
}
