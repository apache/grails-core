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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.Writable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.application.OperatingSystem;
import org.grails.forge.options.FeatureFilter;
import org.grails.forge.options.GormImpl;
import org.grails.forge.options.JdkVersion;
import org.grails.forge.options.Options;
import org.grails.forge.options.ServletImpl;
import org.grails.forge.template.RockerWritable;
import org.grails.forge.template.api.grailsForgeApi;
import org.grails.forge.util.VersionInfo;

import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Main interface on the Grails Application Forge API.
 *
 * @author graemerocher
 * @since 6.0.0
 */
@OpenAPIDefinition(
        info = @Info(
                title = "Grails Forge",
                version = "${api.version}",
                description = "API for Creating Grails Applications",
                license = @License(name = "Apache 2.0")
        )
)
@Controller
public class ApplicationController implements ApplicationTypeOperations {

    private final FeatureOperations featureOperations;
    private final GrailsForgeConfiguration configuration;
    private final MessageSource messageSource;

    /**
     * Default constructor.
     * @param featureOperations The feature operations.
     * @param configuration The Grails Application Forge configuration
     * @param messageSource The message source
     */
    public ApplicationController(FeatureOperations featureOperations, GrailsForgeConfiguration configuration, MessageSource messageSource) {
        this.featureOperations = featureOperations;
        this.configuration = configuration;
        this.messageSource = messageSource;
    }

    /**
     * Information about this instance.
     * @return Information about this instance.
     */
    @Get("/versions")
    VersionDTO getInfo(@Parameter(hidden = true) RequestInfo info) {
        return new VersionDTO()
                 .addLink(Relationship.SELF, info.self());
    }

    /**
     * Provides a description of the API.
     * @param request The request
     * @return A description of the API.
     */
    @Get
    @Produces(MediaType.TEXT_PLAIN)
    @ApiResponse(
            responseCode = "200",
            description = "A textual description of the API",
            content = @Content(mediaType = MediaType.TEXT_PLAIN)
    )
    HttpResponse<Writable> home(HttpRequest<?> request, @Parameter(hidden = true) RequestInfo info) {
        Collection<MediaType> accept = request.accept();
        URI redirectURI = configuration.getRedirectUri().orElse(null);
        if (accept.contains(MediaType.TEXT_HTML_TYPE) && redirectURI != null) {
            return HttpResponse.permanentRedirect(redirectURI);
        } else {
            return HttpResponse.ok(new Writable() {

                @Override
                public void writeTo(Writer out) {
                    // no-op
                }

                @Override
                public void writeTo(OutputStream outputStream, @Nullable Charset charset) {
                    new RockerWritable(new grailsForgeApi()
                            .serverURL(info.getServerURL())
                            .grailsVersion(VersionInfo.getGrailsVersion()))
                            .write(outputStream);
                }
            });
        }
    }

    /**
     * List the application types.
     * @param info the request info
     * @return The types
     */
    @Override
    @Get("/application-types")
    public ApplicationTypeList list(RequestInfo info) {
        List<ApplicationTypeDTO> types = Arrays.stream(ApplicationType.values())
                .map(type -> typeToDTO(type, info, false))
                .collect(Collectors.toList());
        ApplicationTypeList applicationTypeList = new ApplicationTypeList(types);
        applicationTypeList.addLink(
                Relationship.SELF,
                info.self()
        );
        return applicationTypeList;
    }

    /**
     * Get a specific application type.
     * @param type The type
     * @param info The request info
     * @return The type
     */
    @Override
    @Get("/application-types/{type}")
    public ApplicationTypeDTO getType(ApplicationType type, RequestInfo info) {
        return typeToDTO(type, info, true);
    }

    /**
     * List the type features.
     * @param type The features
     * @param requestInfo The request info
     * @param filter features to filter by
     * @return The features
     */
    @Override
    @Get("/application-types/{type}/features{?filter*}")
    public FeatureList features(ApplicationType type,
                                RequestInfo requestInfo,
                                @Nullable FeatureFilter filter) {
        List<FeatureDTO> featureDTOList = featureOperations
                .getFeatures(requestInfo.getLocale(), type, getOptions(filter, requestInfo));

        final FeatureList featureList = new FeatureList(featureDTOList);
        featureList.addLink(
                Relationship.SELF,
                requestInfo.self()
        );
        return featureList;
    }

    @Override
    @Get("/application-types/{type}/features/default{?filter*}")
    public FeatureList defaultFeatures(ApplicationType type,
                                       RequestInfo requestInfo,
                                       @Nullable FeatureFilter filter) {
        List<FeatureDTO> featureDTOList = featureOperations.getDefaultFeatures(requestInfo.getLocale(), type, getOptions(filter, requestInfo));

        final FeatureList featureList = new FeatureList(featureDTOList);
        featureList.addLink(
                Relationship.SELF,
                requestInfo.self()
        );
        return featureList;
    }

    private ApplicationTypeDTO typeToDTO(ApplicationType type, RequestInfo requestInfo, boolean includeFeatures) {
        List<FeatureDTO> features = includeFeatures ? featureOperations.getFeatures(requestInfo.getLocale(), type) : Collections.emptyList();
        features.forEach(featureDTO -> featureDTO.addLink(
                Relationship.DIFF,
                requestInfo.link("/diff/" + type.getName() + "/feature/" + featureDTO.getName())
        ));
        ApplicationTypeDTO dto = new ApplicationTypeDTO(
                type, features, messageSource, MessageSource.MessageContext.of(requestInfo.getLocale())
        );
        dto.addLink(
                Relationship.CREATE,
                requestInfo.link(Relationship.CREATE, type)
        );
        dto.addLink(
                Relationship.PREVIEW,
                requestInfo.link(Relationship.PREVIEW, type)
        );
        dto.addLink(
                Relationship.SELF,
                requestInfo.link(type)
        );
        return dto;
    }

    protected OperatingSystem getOperatingSystem(String userAgent) {
        return UserAgentParser.getOperatingSystem(userAgent);
    }

    protected Options getOptions(@Nullable FeatureFilter filter, RequestInfo requestInfo) {
        if (filter == null) {
            return new Options(null, GormImpl.DEFAULT_OPTION, ServletImpl.DEFAULT_OPTION, JdkVersion.DEFAULT_OPTION, getOperatingSystem(requestInfo.getUserAgent()));
        }

        return new Options(filter.getTest() != null ? filter.getTest().toTestFramework() : null,
                filter.getGorm() == null ? GormImpl.DEFAULT_OPTION : filter.getGorm(),
                filter.getServlet() == null ? ServletImpl.DEFAULT_OPTION : filter.getServlet(),
                filter.getJavaVersion() == null ? JdkVersion.DEFAULT_OPTION : filter.getJavaVersion(),
                getOperatingSystem(requestInfo.getUserAgent()));
    }
}
