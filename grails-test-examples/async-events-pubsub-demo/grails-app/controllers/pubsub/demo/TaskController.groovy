package pubsub.demo

import groovy.transform.CompileStatic

import static grails.async.web.WebPromises.*

@CompileStatic
class TaskController {

    @SuppressWarnings('unused')
	static responseFormats = ['json', 'xml']
	
    def index() {
        task {
            sleep 1000
            render 'good'
        }
    }

    def error() {
        task {
            sleep 1000
            throw new RuntimeException('bad')
        }.onError {
            render text: 'error occurred', status: 500
        }
    }
}
