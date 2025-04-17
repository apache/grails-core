package grails.util

import org.springframework.web.servlet.support.RequestDataValueProcessor

import jakarta.servlet.http.HttpServletRequest

/**
 * Created by graemerocher on 12/04/2017.
 */
class MockRequestDataValueProcessor implements RequestDataValueProcessor {

    Map<String, String> getExtraHiddenFields(HttpServletRequest request) {
        Map<String, String> extraHiddenFields = new HashMap<String, String>()
        extraHiddenFields.put("requestDataValueProcessorHiddenName", "hiddenValue")
        return extraHiddenFields
    }

    String processAction(HttpServletRequest request, String action, String httpMethod) {
        if (action.indexOf("requestDataValueProcessorParamName=paramValue") > -1) {
            action = action.replace("?requestDataValueProcessorParamName=paramValue&", "?")
            action = action.replace("?requestDataValueProcessorParamName=paramValue", "")
            action = action.replace("&requestDataValueProcessorParamName=paramValue", "")
        }
        return action
    }

    String processFormFieldValue(HttpServletRequest request, String name, String value, String type) {
        return value + "_PROCESSED_"
    }

    String processUrl(HttpServletRequest request, String url) {
        String toAppend
        if (url.indexOf("?") > -1) {
            toAppend = "&requestDataValueProcessorParamName=paramValue"
        } else {
            toAppend = "?requestDataValueProcessorParamName=paramValue"
        }
        if (url.indexOf("#") > -1) {
            url = url.replace("#", toAppend + "#")
        } else {
            url = url + toAppend
        }
        return url
    }
}

