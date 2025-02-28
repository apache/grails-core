package org.grails.events

import groovy.transform.AutoFinal
import groovy.transform.CompileStatic

@AutoFinal
@CompileStatic
class EventIdUtils {
    static String eventIdForMethodName(String methodName) {
        if(methodName ==~ /on[A-Z]\S*/) {
            String methodNameWithoutPrefix = methodName.substring(2)
            return "${methodNameWithoutPrefix[0].toLowerCase(Locale.ENGLISH)}${methodNameWithoutPrefix.substring(1)}"
        }
        methodName
    }
}
