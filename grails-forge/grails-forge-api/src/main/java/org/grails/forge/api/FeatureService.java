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

import io.micronaut.context.BeanLocator;
import io.micronaut.context.MessageSource;
import io.micronaut.inject.qualifiers.Qualifiers;
import jakarta.inject.Singleton;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.feature.AvailableFeatures;
import org.grails.forge.feature.DefaultFeature;
import org.grails.forge.feature.Feature;
import org.grails.forge.options.Options;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Implements the {@link FeatureOperations} interface.
 *
 * @author graemerocher
 * @since 6.0.0
 */
@Singleton
public class FeatureService implements FeatureOperations {

    private final List<Feature> features;
    private final BeanLocator beanLocator;
    private final MessageSource messageSource;

    /**
     * Default constructor.
     *
     * @param features      The features
     * @param beanLocator   The bean locator
     * @param messageSource The {@link MessageSource} to support internationalization
     */
    public FeatureService(List<Feature> features, BeanLocator beanLocator, MessageSource messageSource) {
        this.features = features;
        this.beanLocator = beanLocator;
        this.messageSource = messageSource;
    }

    @Override
    public List<FeatureDTO> getAllFeatures(Locale locale) {
        MessageSource.MessageContext context = MessageSource.MessageContext.of(locale);
        return features.stream()
                .filter(Feature::isVisible)
                .map(feature -> new FeatureDTO(feature, messageSource, context))
                .sorted(Comparator.comparing(FeatureDTO::getName))
                .collect(Collectors.toList());
    }

    @Override
    public List<FeatureDTO> getFeatures(Locale locale, ApplicationType type, Options options) {
        MessageSource.MessageContext context = MessageSource.MessageContext.of(locale);
        return beanLocator.getBean(AvailableFeatures.class, Qualifiers.byName(type.getName()))
                .getFeatures()
                .filter(f -> !shouldApplyDefaultFeature(type, f, options))
                .map(feature -> new FeatureDTO(feature, messageSource, context))
                .sorted(Comparator.comparing(FeatureDTO::getName))
                .collect(Collectors.toList());
    }

    private static boolean shouldApplyDefaultFeature(ApplicationType type, Feature f, Options options) {
        return f instanceof DefaultFeature &&
                ((DefaultFeature) f).shouldApply(type, options, new HashSet<>());
    }

    @Override
    public List<FeatureDTO> getDefaultFeatures(Locale locale, ApplicationType type, Options options) {
        MessageSource.MessageContext context = MessageSource.MessageContext.of(locale);
        return beanLocator.getBean(AvailableFeatures.class, Qualifiers.byName(type.getName()))
                .getFeatures()
                .filter(f -> f instanceof DefaultFeature)
                .map(DefaultFeature.class::cast)
                .filter(f -> f.shouldApply(
                        type,
                        options,
                        new HashSet<>()))
                .map(feature -> new FeatureDTO(feature, messageSource, context))
                .sorted(Comparator.comparing(FeatureDTO::getName))
                .collect(Collectors.toList());
    }
}
