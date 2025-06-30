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
import org.grails.forge.options.TestFramework;
import org.grails.forge.util.NameUtils;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.stream.Collectors;

/**
 * DTO objects for {@link TestFramework}.
 *
 * @author graemerocher
 * @since 6.0.0
 */
@Schema(name = "TestFrameworkInfo")
@Introspected
public class TestFrameworkDTO extends Linkable implements Named, Described, Selectable<TestFramework> {
    static final String MESSAGE_PREFIX = GrailsForgeConfiguration.PREFIX + ".testFramework.";
    private final String name;
    private final String description;
    private final LanguageDTO defaultLanguage;
    private final List<LanguageDTO> supportedLanguages;
    private final TestFramework value;

    /**
     * @param testFramework The testFramework
     */
    public TestFrameworkDTO(TestFramework testFramework) {
        this.value = testFramework;
        this.name = testFramework.toString();
        this.description = testFramework.name();
        this.defaultLanguage = new LanguageDTO(testFramework.getDefaultLanguage());
        this.supportedLanguages = testFramework.getSupportedLanguages()
                .stream()
                .map(LanguageDTO::new)
                .collect(Collectors.toList());
    }

    /**
     * @param name the name
     * @param description The description
     */
    @Creator
    @Internal
    TestFrameworkDTO(TestFramework value, String name, String description, LanguageDTO defaultLanguage, List<LanguageDTO> supportedLanguages) {
        this.value = value;
        this.name = name;
        this.description = description;
        this.defaultLanguage = defaultLanguage;
        this.supportedLanguages = supportedLanguages;
    }

    /**
     * i18n constructor.
     * @param testFramework The type
     * @param messageSource The message source
     * @param messageContext The message context
     */
    @Internal
    TestFrameworkDTO(TestFramework testFramework, MessageSource messageSource, MessageSource.MessageContext messageContext) {
        this.value = testFramework;
        this.name = testFramework.toString();
        this.description = messageSource.getMessage(MESSAGE_PREFIX + name + ".description", messageContext, NameUtils.getNaturalNameOfEnum(name));
        this.defaultLanguage = new LanguageDTO(testFramework.getDefaultLanguage());
        this.supportedLanguages = testFramework.getSupportedLanguages()
                .stream()
                .map(LanguageDTO::new)
                .collect(Collectors.toList());
    }

    @Override
    @Schema(description = "A description of the testFramework")
    public String getDescription() {
        return description;
    }

    @Override
    @Schema(description = "The name of the testFramework")
    @NonNull
    public String getName() {
        return name;
    }

    @Override
    @Schema(description = "The value of the testFramework for select options")
    @NonNull
    public TestFramework getValue() {
        return value;
    }

    @Override
    @Schema(description = "The label of the testFramework for select options")
    public String getLabel() {
        return description;
    }
}
