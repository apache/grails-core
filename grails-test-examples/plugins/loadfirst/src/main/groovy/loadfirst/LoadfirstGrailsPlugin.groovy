package loadfirst

import grails.plugins.*

class LoadfirstGrailsPlugin extends Plugin {

   // the version or versions of Grails the plugin is designed for
    def grailsVersion = "3.0.0.BUILD-SNAPSHOT > *"
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
        "grails-app/views/error.gsp"
    ]

    // TODO Fill in these fields
    def title = "Loadfirst" // Headline display name of the plugin
    def author = "Your name"
    def authorEmail = ""
    def description = '''\
This plugin should be loaded before the loadsecond plugin because loadsecond expresses loadAfter=['loadfirst'].
'''
    def profiles = ['web']

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugin/loadfirst"
}
