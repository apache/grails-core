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

import org.springframework.context.MessageSource;
import java.util.Locale;
import jakarta.annotation.Nonnull;
import org.grails.forge.application.ApplicationType;

import java.util.List;

/**
 * DTO objects for {@link ApplicationType}.
 *
 * @author graemerocher
 * @since 6.0.0
 */
public class ApplicationTypeDTO extends Linkable implements Selectable<ApplicationType> {

    static final String MESSAGE_PREFIX = GrailsForgeConfiguration.PREFIX + ".application-types.";
    private final String name;
    private final List<FeatureDTO> features;
    private final String title;
    private final String description;
    private final ApplicationType value;

    /**
     * @param type The type
     * @param features The available features
     */
    public ApplicationTypeDTO(ApplicationType type, List<FeatureDTO> features) {
        this.value = type;
        this.name = type.getName();
        this.features = features;
        this.title = type.getTitle();
        this.description = type.getDescription();
    }

    /**
     * @param name the name
     * @param features The available features
     */
    ApplicationTypeDTO(ApplicationType value,
                       String name,
                       String title,
                       String description,
                       List<FeatureDTO> features) {
        this.value = value;
        this.name = name;
        this.features = features;
        this.title = title;
        this.description = description;
    }

    /**
     * i18n constructor.
     * @param type The type
     * @param features The features
     * @param messageSource The message source
     * @param messageContext The message context
     */
    public ApplicationTypeDTO(ApplicationType type, List<FeatureDTO> features, MessageSource messageSource, Locale locale) {
        this.value = type;
        String name = type.getName();
        this.name = name;
        this.features = features;
        this.title = ForgeMessages.message(messageSource, locale, MESSAGE_PREFIX + name + ".title", type.getTitle());
        this.description = ForgeMessages.message(messageSource, locale, MESSAGE_PREFIX + name + ".description", type.getDescription());
    }

        public String getTitle() {
        return title;
    }

        public List<FeatureDTO> getFeatures() {
        return features;
    }

        public String getDescription() {
        return description;
    }

        @Nonnull
    public String getName() {
        return name;
    }

    @Override
        public ApplicationType getValue() {
        return value;
    }

    @Override
        public String getLabel() {
        return title;
    }
}
