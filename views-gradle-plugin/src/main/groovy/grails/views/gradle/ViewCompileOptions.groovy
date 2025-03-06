package grails.views.gradle

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.compile.GroovyForkOptions
import javax.inject.Inject

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class ViewCompileOptions implements Serializable {

    private static final long serialVersionUID = 0L

    @Input
    final Property<String> encoding

    @Nested
    GroovyForkOptions forkOptions

    @Inject
    ViewCompileOptions(ObjectFactory objects) {
        encoding = objects.property(String).convention('UTF-8')
        forkOptions = objects.newInstance(GroovyForkOptions)
    }
}
