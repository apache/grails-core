/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.taglib.encoder

import groovy.transform.CompileStatic
import grails.core.GrailsApplication
import groovy.util.logging.Commons
import org.grails.encoder.CodecLookupHelper
import org.grails.encoder.Encoder
import org.grails.taglib.encoder.OutputEncodingStackAttributes.Builder

/**
 * Helper methods for {@link #withCodec} feature.
 *
 * @author Lari Hotari
 * @since 2.3
 */
@CompileStatic
@Commons
class WithCodecHelper {

    /** all is the key to set all codecs at once */
    public static final String ALL_CODECS_FALLBACK_KEY_NAME = "all"
    /** name is the key to set out and expression codecs at once */
    public static final String OUT_AND_EXPRESSION_CODECS_FALLBACK_KEY_NAME = "name"

    private static final String ALREADY_CANONICAL_KEY_NAME = "_canonical_"


    /**
     * Executes closure with given codecs.
     *
     * codecInfo parameter can be a single String value or a java.util.Map.
     * When it's a single String value, "out", "expression" and "taglib" get set with the given codec
     * When it's a java.util.Map, these keys get used:
     * <ul>
     * <li>scriptlet - escapes output from scriptlets to output (the codec attached to "out" writer instance in GSP scriptlets)</li>
     * <li>taglib - escapes output from taglibs to output (the codec attached to "out" writer instance in taglibs)</li>
     * <li>expression - escapes values inside ${} to output</li>
     * <li>static - escapes the static html parts coming from the GSP file to output</li>
     * </ul>
     * These keys set several codecs at once:
     * <ul>
     * <li>all - sets scriptlet, taglib, expression and static</li>
     * <li>name - sets scriptlet, taglib and expression</li>
     * </ul>
     * In addition there is
     * <ul>
     * <li>inherit (boolean) - defaults to true. Controls whether codecs should be inherited to deeper level (taglib calls)</li>
     * <li>replaceonly (boolean) - defaults to false. Codecs will be only replaced if the previous inherited codec is safe.</li> 
     * </ul>
     *
     * @param grailsApplication the grailsApplication instance
     * @param codecInfo this parameter is explained above
     * @param closure the closure to execute
     * @return the return value of the closure
     */
    static <T> T withCodec(GrailsApplication grailsApplication, Object codecInfo, Closure<T> closure) {
        OutputEncodingStack outputStack=OutputEncodingStack.currentStack()
        try {
            outputStack.push(createOutputStackAttributesBuilder(codecInfo, grailsApplication).build(), false)
            return closure.call()
        } finally {
            outputStack.pop()
        }
    }

    /**
     * Creates a builder for building a new {@link OutputEncodingStackAttributes} instance
     *
     * @param codecInfo the codec info, see {@link #withCodec} method for more info
     * @param grailsApplication the grails application
     * @return the builder instance for building {@link OutputEncodingStackAttributes} instance
     */
    static Builder createOutputStackAttributesBuilder(Object codecInfo, GrailsApplication grailsApplication) {
        Builder builder=new Builder()
        builder.inheritPreviousEncoders(true)
        if (codecInfo != null) {
            Map<String, Object> codecInfoMap = makeSettingsCanonical(codecInfo)
            Map<String, Encoder> encoders = [:]
            codecInfoMap.each { String codecWriterName, Object codecName ->
                if (codecName instanceof String && !encoders.containsKey(codecName)) {
                    String codecNameString = codecName as String
                    encoders.put(codecNameString, lookupEncoder(grailsApplication, codecNameString))
                }
            }
            builder.outEncoder(lookupEncoderFromMap(encoders, (String)codecInfoMap.get(OutputEncodingSettings.OUT_CODEC_NAME)))
            builder.taglibEncoder(lookupEncoderFromMap(encoders, (String)codecInfoMap.get(OutputEncodingSettings.TAGLIB_CODEC_NAME)))
            builder.expressionEncoder(lookupEncoderFromMap(encoders, (String)codecInfoMap.get(OutputEncodingSettings.EXPRESSION_CODEC_NAME)))
            builder.staticEncoder(lookupEncoderFromMap(encoders, (String)codecInfoMap.get(OutputEncodingSettings.STATIC_CODEC_NAME)))
            if (codecInfoMap.containsKey(OutputEncodingSettings.INHERIT_SETTING_NAME)) {
                builder.inheritPreviousEncoders(codecInfoMap.get(OutputEncodingSettings.INHERIT_SETTING_NAME) as boolean)
            }
            if (codecInfoMap.containsKey(OutputEncodingSettings.REPLACE_ONLY_SETTING_NAME)) {
                builder.replaceOnly(codecInfoMap.get(OutputEncodingSettings.REPLACE_ONLY_SETTING_NAME) as boolean)
            }
        }
        return builder
    }

    static Map<String, Object> makeSettingsCanonical(codecInfo) {
        if (!codecInfo) {
            return null
        }
        Map<String, Object> codecInfoMap =[:]
        if (codecInfo instanceof Map) {
            if (codecInfo.get(ALREADY_CANONICAL_KEY_NAME)) {
                return (Map<String, Object>)codecInfo
            }
            String allFallback = null
            String nameFallback = null
            (Map<String,String>)((Map)codecInfo).each { k, v ->
                String codecWriterName = k.toString().toLowerCase() - 'codec'
                if (codecWriterName == OutputEncodingSettings.INHERIT_SETTING_NAME || codecWriterName == OutputEncodingSettings.REPLACE_ONLY_SETTING_NAME) {
                    codecInfoMap.put(codecWriterName, convertToBoolean(v))
                } else {
                    String codecName=v?.toString() ?: 'none'
                    if (OutputEncodingSettings.VALID_CODEC_SETTING_NAMES.contains(codecWriterName)) {
                        codecInfoMap.put(codecWriterName, codecName)
                    } else if (codecWriterName == WithCodecHelper.ALL_CODECS_FALLBACK_KEY_NAME) {
                        allFallback = codecName
                    } else if (codecWriterName == WithCodecHelper.OUT_AND_EXPRESSION_CODECS_FALLBACK_KEY_NAME) {
                        nameFallback = codecName
                    }
                }
            }

            if (allFallback || nameFallback) {
                for(String codecWriterName : OutputEncodingSettings.VALID_CODEC_SETTING_NAMES) {
                    String codecName=codecInfoMap.get(codecWriterName)?.toString()
                    if (!codecName) {
                        if (nameFallback && codecWriterName != OutputEncodingSettings.STATIC_CODEC_NAME) {
                            codecName = nameFallback
                        } else if (allFallback) {
                            codecName = allFallback
                        }
                        codecInfoMap.put(codecWriterName, codecName)
                    }
                }
            }
        } else {
            String codecName = codecInfo.toString()
            for(String codecWriterName : OutputEncodingSettings.VALID_CODEC_SETTING_NAMES) {
                if (codecWriterName != OutputEncodingSettings.STATIC_CODEC_NAME) {
                    codecInfoMap.put(codecWriterName, codecName)
                }
            }
        }
        codecInfoMap.put(ALREADY_CANONICAL_KEY_NAME, true)
        codecInfoMap
    }

    private static boolean convertToBoolean(v) {
        Boolean booleanValue = v as Boolean
        if (booleanValue && v instanceof CharSequence && (v.toString()=="false" || v.toString()=="no")) {
            booleanValue = false
        }
        return booleanValue
    }

    private static Encoder lookupEncoderFromMap(Map<String, Encoder> encoders, String codecName) {
        codecName == null ? null : encoders[codecName]
    }

    static Encoder lookupEncoder(GrailsApplication grailsApplication, String codecName) {
        CodecLookupHelper.lookupEncoder(grailsApplication, codecName)
    }

    static Map<String, Object> mergeSettingsAndMakeCanonical(Object currentSettings,
            Map<String, Object> parentSettings) {
        Map<String, Object> codecInfoMap
        if (currentSettings) {
            Map<String, Object> canonicalCodecInfo = makeSettingsCanonical(currentSettings)
            if (parentSettings != null) {
                codecInfoMap = new HashMap<String, Object>()
                codecInfoMap.putAll(parentSettings)
                codecInfoMap.putAll(canonicalCodecInfo)
            } else {
                codecInfoMap = canonicalCodecInfo
            }
            codecInfoMap = (Map<String, Object>)codecInfoMap.asImmutable()
        }
        if (codecInfoMap == null) {
            codecInfoMap = parentSettings
        }
        return codecInfoMap
    }
}
