package grails.plugin.scaffolding

import grails.plugins.*
import grails.util.Environment
import grails.util.Metadata

class ScaffoldingGrailsPlugin extends Plugin {

   // the version or versions of Grails the plugin is designed for
    def grailsVersion = "4.0.0.BUILD-SNAPSHOT > *"
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
        "grails-app/views/error.gsp"
    ]

    def title = "Scaffolding Plugin" // Headline display name of the plugin
    def author = "Graeme Rocher"
    def authorEmail = "info@grails.org"
    def description = '''\
Plugin that generates scaffolded controllers and views for a Grails application.
'''

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugin/scaffolding"

    // Extra (optional) plugin metadata

    // License: one of 'APACHE', 'GPL2', 'GPL3'
    def license = "APACHE"


    // Location of the plugin's issue tracker.
    def issueManagement = [ system: "Github", url: "https://github.com/grails3-plugins/scaffolding/issues" ]

    // Online location of the plugin's browseable source code.
    def scm = [ url: "https://github.com/grails3-plugins/scaffolding" ]

    def loadAfter = ["groovyPages"]

    @Override
    Closure doWithSpring() { {->
        Environment env = Environment.current
        boolean reloadEnabled = env.isReloadEnabled() || (Metadata.getCurrent().isDevelopmentEnvironmentAvailable() && env == Environment.DEVELOPMENT)

        // Configure a Spring MVC view resolver
        jspViewResolver(ScaffoldingViewResolver) { bean ->
            bean.lazyInit = true
            bean.parent = "abstractViewResolver"
            enableReload = reloadEnabled
        }
    }}
}
