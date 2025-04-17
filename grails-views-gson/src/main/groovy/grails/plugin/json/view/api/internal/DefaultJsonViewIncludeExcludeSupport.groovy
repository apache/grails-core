package grails.plugin.json.view.api.internal

import groovy.transform.CompileStatic
import org.grails.core.util.IncludeExcludeSupport

@CompileStatic
class DefaultJsonViewIncludeExcludeSupport extends IncludeExcludeSupport<String> {

    DefaultJsonViewIncludeExcludeSupport(List<String> defaultIncludes, List<String> defaultExcludes) {
        super(defaultIncludes, defaultExcludes)
    }

    @Override
    boolean shouldInclude(List<String> incs, List excs, String object) {
        def i = object.lastIndexOf('.')
        String unqualified = i > -1 ? object.substring(i + 1) : null
        return super.shouldInclude(incs, excs, object) && (unqualified == null || (includes(defaultIncludes, unqualified) && !excludes(defaultExcludes, unqualified)))
    }

    @Override
    boolean includes(List<String> includes, String object) {
        includes == null ||
                includes.contains(object) ||
                includes.any { object.startsWith(it + ".") } ||
                includes.any { it.startsWith(object + ".") }
    }
}
