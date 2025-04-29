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

package grails.util;

import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.servlet.support.RequestDataValueProcessor;

public class MockRequestDataValueProcessor implements RequestDataValueProcessor {

    public Map<String, String> getExtraHiddenFields(HttpServletRequest request) {
        Map<String, String> extraHiddenFields = new HashMap<String, String>();
        extraHiddenFields.put("requestDataValueProcessorHiddenName", "hiddenValue");
        return extraHiddenFields;
    }

    public String processAction(HttpServletRequest request, String action, String httpMethod) {
        if (action.indexOf("requestDataValueProcessorParamName=paramValue") > -1) {
            action = action.replace("?requestDataValueProcessorParamName=paramValue&", "?");
            action = action.replace("?requestDataValueProcessorParamName=paramValue", "");
            action = action.replace("&requestDataValueProcessorParamName=paramValue", "");
        }
        return action;
    }

    public String processFormFieldValue(HttpServletRequest request, String name, String value, String type) {
        return value + "_PROCESSED_";
    }

    public String processUrl(HttpServletRequest request, String url) {
        String toAppend;
        if (url.indexOf("?") > -1) {
            toAppend = "&requestDataValueProcessorParamName=paramValue";
        } else {
            toAppend = "?requestDataValueProcessorParamName=paramValue";
        }
        if (url.indexOf("#") > -1) {
            url = url.replace("#", toAppend + "#");
        } else {
            url = url + toAppend;
        }
        return url;
    }
}
