/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.web.pages;

import grails.config.Config;
import grails.config.Settings;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import grails.core.GrailsApplication;
import org.grails.encoder.CodecLookupHelper;
import org.grails.encoder.Encoder;

public class FilteringCodecsByContentTypeSettings {
    private static final String WILDCARD_CONTENT_TYPE = "*/*";

    public static final String CONFIG_PROPERTY_CODEC_FOR_CONTENT_TYPE = "grails.views.filteringCodecForContentType";
    public static final String BEAN_NAME="filteringCodecsByContentTypeSettings";

    Map<String, Encoder> contentTypeToEncoderMapping;
    Map<Pattern, Encoder> contentTypePatternToEncoderMapping;

    public FilteringCodecsByContentTypeSettings(GrailsApplication grailsApplication) {
        initialize(grailsApplication);
    }

    @SuppressWarnings("rawtypes")
    public void initialize(GrailsApplication grailsApplication) {
        contentTypeToEncoderMapping=null;
        contentTypePatternToEncoderMapping=null;
        Map codecForContentTypeConfig = getConfigSettings(grailsApplication.getConfig());
        if (codecForContentTypeConfig != null) {
            contentTypeToEncoderMapping=new LinkedHashMap<String, Encoder>();
            contentTypePatternToEncoderMapping=new LinkedHashMap<Pattern, Encoder>();
            Map codecForContentTypeMapping=(Map)codecForContentTypeConfig;
            for(Iterator i=codecForContentTypeMapping.entrySet().iterator();i.hasNext();) {
                Map.Entry entry=(Map.Entry)i.next();
                Encoder encoder=CodecLookupHelper.lookupEncoder(grailsApplication, String.valueOf(entry.getValue()));
                if (entry.getKey() instanceof Pattern) {
                    contentTypePatternToEncoderMapping.put((Pattern)entry.getKey(), encoder);
                } else {
                    contentTypeToEncoderMapping.put(String.valueOf(entry.getKey()), encoder);
                }
            }
        }
    }

    public Encoder getEncoderForContentType(String contentType) {
        if (contentTypeToEncoderMapping==null) {
            return null;
        }
        if (contentType==null) {
            contentType=WILDCARD_CONTENT_TYPE;
        }
        Encoder encoder=contentTypeToEncoderMapping.get(contentType);
        if (encoder != null) {
            return encoder;
        }
        for(Map.Entry<Pattern, Encoder> entry : contentTypePatternToEncoderMapping.entrySet()) {
            if (entry.getKey().matcher(contentType).matches()) {
                return encoder;
            }
        }
        return contentTypeToEncoderMapping.get(WILDCARD_CONTENT_TYPE);
    }
    
    protected Map getConfigSettings(Config config) {
        return config.getProperty(Settings.VIEWS_FILTERING_CODEC_FOR_CONTENT_TYPE, Map.class);
    }
}
