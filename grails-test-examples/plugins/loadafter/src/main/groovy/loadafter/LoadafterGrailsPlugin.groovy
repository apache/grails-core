package loadafter

import grails.plugins.*

class LoadafterGrailsPlugin extends Plugin {

    def title = "Load After" // Headline display name of the plugin
    def author = "Your name"
    def authorEmail = ""
    def description = '''\
Brief summary/description of the plugin.
'''
    def profiles = ['web']

    def loadAfter = ['springSecurityCore']

}
