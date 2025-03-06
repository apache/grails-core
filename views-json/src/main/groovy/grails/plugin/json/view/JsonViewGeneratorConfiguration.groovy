package grails.plugin.json.view

import groovy.transform.CompileStatic

/**
 * Created by jameskleeh on 11/8/16.
 */
@CompileStatic
class JsonViewGeneratorConfiguration {

    Boolean escapeUnicode = false

    String dateFormat = "yyyy-MM-dd'T'HH:mm:ss'Z'"

    String timeZone = "GMT"

    String locale = "en/US"
}
