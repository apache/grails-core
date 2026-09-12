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
import java.util.Collections;
import java.util.List;

import org.grails.forge.feature.Feature;
import org.grails.forge.feature.OneOfFeature;

/**
 * Represents an application feature.
 *
 * @author graemerocher
 * @since 6.0.0
 */
public class FeatureDTO extends Linkable {

    static final String MESSAGE_PREFIX = GrailsForgeConfiguration.PREFIX + ".features.";

    private final String name;
    private final String title;
    private final String description;
    private final String category;
    private final boolean preview;
    private final boolean community;
    private final List<String> dependentFeatures;
    private final String oneOfGroup;

    /**
     * Default constructor.
     * @param feature The feature
     * @param messageSource The message source
     * @param messageContext The messageContext
     */
    public FeatureDTO(Feature feature, MessageSource messageSource, Locale locale) {
        this.name = feature.getName();
        this.title = ForgeMessages.message(messageSource, locale, MESSAGE_PREFIX + this.name + ".title", feature.getTitle());
        this.description = ForgeMessages.message(messageSource, locale, MESSAGE_PREFIX + this.name + ".description", feature.getDescription());
        this.category = feature.getCategory();
        this.preview = feature.isPreview();
        this.community = feature.isCommunity();
        this.dependentFeatures = feature.getDependentFeatures();
        this.oneOfGroup = feature instanceof OneOfFeature ? ((OneOfFeature) feature).getFeatureClass().getSimpleName() : null;
    }

    /**
     * Default constructor.
     * @param name The name
     * @param title The title
     * @param description The description
     * @param category The category
     */
    public FeatureDTO(String name, String title, String description, String category) {
        this.name = name;
        this.title = title;
        this.description = description;
        this.category = category;
        this.preview = false;
        this.community = false;
        this.dependentFeatures = Collections.emptyList();
        this.oneOfGroup = null;
    }

    /**
     * @return The name of the feature
     */
        public String getName() {
        return name;
    }

    /**
     * @return The title of the feature
     */
        public String getTitle() {
        return title;
    }

    /**
     * @return The description of the feature
     */
        public String getDescription() {
        return description;
    }

    /**
     * @return The category to which this feature belongs to
     */
        public String getCategory() {
        return category;
    }

    /**
     * @return the names of features added automatically when this feature is selected
     */
        public List<String> getDependentFeatures() {
        return dependentFeatures;
    }

    /**
     * @return the mutual-exclusion group this feature belongs to, if any
     */
        public String getOneOfGroup() {
        return oneOfGroup;
    }

    /**
     * @return Is the feature a preview status feature
     */
        public boolean isPreview() {
        return preview;
    }

    /**
     * @return Is the feature a community contributed feature.
     */
        public boolean isCommunity() {
        return community;
    }
}
