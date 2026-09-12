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

import org.grails.forge.application.ApplicationType;
import org.grails.forge.options.FeatureFilter;
import org.grails.forge.options.GormImpl;
import org.grails.forge.options.JdkVersion;
import org.grails.forge.options.Options;
import org.grails.forge.options.ServletImpl;
import org.grails.forge.template.RockerWritable;
import org.grails.forge.template.api.grailsForgeApi;
import org.grails.forge.util.VersionInfo;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ForgeApplicationService {

    private final FeatureOperations featureOperations;
    private final GrailsForgeConfiguration configuration;
    private final MessageSource messageSource;

    public ForgeApplicationService(FeatureOperations featureOperations, GrailsForgeConfiguration configuration, MessageSource messageSource) {
        this.featureOperations = featureOperations;
        this.configuration = configuration;
        this.messageSource = messageSource;
    }

    public GrailsForgeConfiguration getConfiguration() {
        return configuration;
    }

    public VersionDTO getInfo(RequestInfo info) {
        return new VersionDTO().addLink(Relationship.SELF, info.self());
    }

    public String homeText(RequestInfo info) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        new RockerWritable(new grailsForgeApi()
                .serverURL(info.getServerURL())
                .grailsVersion(VersionInfo.getGrailsVersion()))
                .write(outputStream);
        return outputStream.toString(StandardCharsets.UTF_8);
    }

    public ApplicationTypeList list(RequestInfo info) {
        List<ApplicationTypeDTO> types = Arrays.stream(ApplicationType.values())
                .map(type -> typeToDTO(type, info, false))
                .collect(Collectors.toList());
        ApplicationTypeList applicationTypeList = new ApplicationTypeList(types);
        applicationTypeList.addLink(Relationship.SELF, info.self());
        return applicationTypeList;
    }

    public ApplicationTypeDTO getType(ApplicationType type, RequestInfo info) {
        return typeToDTO(type, info, true);
    }

    public FeatureList features(ApplicationType type, RequestInfo requestInfo, FeatureFilter filter) {
        List<FeatureDTO> featureDTOList = featureOperations
                .getFeatures(requestInfo.getLocale(), type, getOptions(filter, requestInfo));
        FeatureList featureList = new FeatureList(featureDTOList);
        featureList.addLink(Relationship.SELF, requestInfo.self());
        return featureList;
    }

    public FeatureList defaultFeatures(ApplicationType type, RequestInfo requestInfo, FeatureFilter filter) {
        List<FeatureDTO> featureDTOList = featureOperations.getDefaultFeatures(requestInfo.getLocale(), type, getOptions(filter, requestInfo));
        FeatureList featureList = new FeatureList(featureDTOList);
        featureList.addLink(Relationship.SELF, requestInfo.self());
        return featureList;
    }

    private ApplicationTypeDTO typeToDTO(ApplicationType type, RequestInfo requestInfo, boolean includeFeatures) {
        List<FeatureDTO> features = includeFeatures ? featureOperations.getFeatures(requestInfo.getLocale(), type) : Collections.emptyList();
        features.forEach(featureDTO -> featureDTO.addLink(
                Relationship.DIFF,
                requestInfo.link("/diff/" + type.getName() + "/feature/" + featureDTO.getName())
        ));
        ApplicationTypeDTO dto = new ApplicationTypeDTO(type, features, messageSource, requestInfo.getLocale());
        dto.addLink(Relationship.CREATE, requestInfo.link(Relationship.CREATE, type));
        dto.addLink(Relationship.PREVIEW, requestInfo.link(Relationship.PREVIEW, type));
        dto.addLink(Relationship.SELF, requestInfo.link(type));
        return dto;
    }

    protected Options getOptions(FeatureFilter filter, RequestInfo requestInfo) {
        if (filter == null) {
            return new Options(null, GormImpl.DEFAULT_OPTION, ServletImpl.DEFAULT_OPTION, JdkVersion.DEFAULT_OPTION, UserAgentParser.getOperatingSystem(requestInfo.getUserAgent()));
        }
        return new Options(filter.getReloading() != null ? filter.getReloading() : null,
                filter.getGorm() == null ? GormImpl.DEFAULT_OPTION : filter.getGorm(),
                filter.getServlet() == null ? ServletImpl.DEFAULT_OPTION : filter.getServlet(),
                filter.getJavaVersion() == null ? JdkVersion.DEFAULT_OPTION : filter.getJavaVersion(),
                UserAgentParser.getOperatingSystem(requestInfo.getUserAgent()));
    }
}
