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

@CompileStatic
class OutputEncodingSettings {
    /** scriptlet codec escapes the static html parts coming from the GSP file scriptlets to output */
    public static final String OUT_CODEC_NAME="scriptlet"
    /** expression codec escapes values inside ${} to output */
    public static final String EXPRESSION_CODEC_NAME="expression"
    /** staticparts escapes the static html parts coming from the GSP file to output */
    public static final String STATIC_CODEC_NAME="staticparts"
    /** taglib codec escapes taglib output */
    public static final String TAGLIB_CODEC_NAME="taglib"
    /** taglibdefault codec setting name is the fallback for taglib default codec, taglibCodec in gsp directive uses this setting internally */
    public static final String TAGLIB_DEFAULT_CODEC_NAME="taglibdefault"
    /** allow inheriting codecs from parent levels */
    public static final String INHERIT_SETTING_NAME="inherit"
    /** only use for safe codecs for replacement */
    public static final String REPLACE_ONLY_SETTING_NAME="replaceonly"

    public static final String CONFIG_PROPERTY_GSP_CODECS = "grails.views.gsp.codecs"

    public static final String CONFIG_PROPERTY_DEFAULT_CODEC = "grails.views.default.codec"

    public static final Set<String> VALID_CODEC_SETTING_NAMES =
        ([OUT_CODEC_NAME, EXPRESSION_CODEC_NAME, STATIC_CODEC_NAME, TAGLIB_CODEC_NAME, TAGLIB_DEFAULT_CODEC_NAME] as Set).asImmutable()

    private static final Map<String, String> defaultSettings =
        [(EXPRESSION_CODEC_NAME):     'html',
         (STATIC_CODEC_NAME):         'none',
         (OUT_CODEC_NAME):            'none',
         (TAGLIB_CODEC_NAME):         'none',
         (TAGLIB_DEFAULT_CODEC_NAME): 'none']


    static String getDefaultValue(String codecName) {
        defaultSettings.get(codecName)
    }


}
