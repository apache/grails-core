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
package org.grails.forge.api;

import io.micronaut.context.MessageSource;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.naming.Described;
import io.micronaut.core.naming.Named;
import org.grails.forge.defaults.IncludesDefaults;
import org.grails.forge.defaults.LanguageDefaults;
import org.grails.forge.options.Language;
import org.grails.forge.util.NameUtils;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO objects for {@link Language}.
 *
 * @author graemerocher
 * @since 6.0.0
 */
@Schema(name = "LanguageInfo")
@Introspected
public class LanguageDTO extends Linkable implements Named, Described, Selectable<Language>, IncludesDefaults<LanguageDefaults> {
    static final String MESSAGE_PREFIX = GrailsForgeConfiguration.PREFIX + ".language.";
    private final String name;
    private final String extension;
    private final String description;
    private final Language value;

    /**
     * @param language The language
     */
    public LanguageDTO(Language language) {
        this.value = language;
        this.name = language.getName();
        this.extension = language.getExtension();
        this.description = language.getName();
    }

    /**
     * @param name the name
     * @param extension The extension
     * @param description The description
     */
    @Creator
    @Internal
    LanguageDTO(Language value, String name, String extension, String description) {
        this.value = value;
        this.name = name;
        this.extension = extension;
        this.description = description;
    }

    /**
     * i18n constructor.
     * @param language The type
     * @param messageSource The message source
     * @param messageContext The message context
     */
    @Internal
    LanguageDTO(Language language, MessageSource messageSource, MessageSource.MessageContext messageContext) {
        this.value = language;
        String name = language.getName();
        this.name = name;
        this.extension = language.getExtension();
        this.description = messageSource.getMessage(MESSAGE_PREFIX + name + ".description", messageContext, name);

    }

    @Schema(description = "The extension of the language")
    public String getExtension() {
        return extension;
    }

    @Override
    @Schema(description = "A description of the language")
    public String getDescription() {
        return description;
    }

    @Override
    @Schema(description = "The name of the language")
    @NonNull
    public String getName() {
        return name;
    }

    @Override
    @Schema(description = "The value of the language for select options")
    public Language getValue() {
        return value;
    }

    @Override
    @Schema(description = "The label of the language for select options")
    public String getLabel() {
        return NameUtils.getNaturalNameOfEnum(name);
    }

    @Override
    @Schema(description = "The default values that correlate to the language")
    public LanguageDefaults getDefaults() {
        return this.value.getDefaults();
    }
}
