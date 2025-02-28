package loadsecond

import grails.plugins.*

class LoadsecondGrailsPlugin extends Plugin {

   // the version or versions of Grails the plugin is designed for
    def grailsVersion = "3.0.0.BUILD-SNAPSHOT > *"
    // resources that are excluded from plugin packaging
    def pluginExcludes = [
        "grails-app/views/error.gsp"
    ]

    // TODO Fill in these fields
    def title = "Loadsecond" // Headline display name of the plugin
    def author = "Your name"
    def authorEmail = ""
    def description = '''\
This plugin should be loaded after the loadfirst plugin.
'''
    def profiles = ['web']

    // URL to the plugin's documentation
    def documentation = "http://grails.org/plugin/loadsecond"

    def loadAfter = ['loadfirst']
}
