package issue11767.plugin

import grails.plugins.*

class Issue11767GrailsPlugin extends Plugin {

    def title = 'Issue 11767'
    def author = 'Mattias Reichel'
    def authorEmail = ''
    def description = ''

    Closure doWithSpring() {{ ->
        pluginGroovySpringBean(PluginGroovySpringBean)
    }}
}