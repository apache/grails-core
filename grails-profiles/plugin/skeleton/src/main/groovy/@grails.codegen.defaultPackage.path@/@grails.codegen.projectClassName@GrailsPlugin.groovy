package @grails.codegen.defaultPackage@

import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.core.env.Environment

import grails.plugins.Plugin
import grails.plugins.metadata.GrailsPluginDescriptor

/**
 * The @grails.codegen.projectNaturalName@ plugin. Its name, ordering and the rest of its metadata are
 * declared by the annotation; its version is the project version. The published POM carries the
 * description, license and developers, from the grailsPublish block in build.gradle.
 */
@GrailsPluginDescriptor(
        // Plugins this one loads after, or needs: loadAfter = ['controllers'], dependsOn = ['core']
        // Artefact classes left out of the plugin's descriptor: pluginExcludes = ['com/example/demo/**']
)
class @grails.codegen.projectClassName@GrailsPlugin extends Plugin {

    BeanRegistrar beanRegistrar() {
        return { BeanRegistry registry, Environment environment ->
            // TODO Register runtime Spring beans (optional)
        } as BeanRegistrar
    }

    void doWithApplicationContext() {
        // TODO Implement post initialization spring config, such as registering dynamic methods (optional)
    }

    void onChange(Map<String, Object> event) {
        // TODO Implement code that is executed when any artefact that this plugin is
        // watching is modified and reloaded. The event contains: event.source,
        // event.application, event.manager, event.ctx, and event.plugin.
    }

    void onConfigChange(Map<String, Object> event) {
        // TODO Implement code that is executed when the project configuration changes.
        // The event is the same as for 'onChange'.
    }

    void onShutdown(Map<String, Object> event) {
        // TODO Implement code that is executed when the application shuts down (optional)
    }
}
