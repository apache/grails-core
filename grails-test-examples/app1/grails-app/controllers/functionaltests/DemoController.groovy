package functionaltests

class DemoController {

    def show() {
        [name: 'Jeff']
    }

    def another() {
        render view:"show", model:[name: 'Jeff']
    }

    def sayHello(String actionTakenInInterceptor) {
        render "<html><body>Hi There! Special Action: $actionTakenInInterceptor</body></html>"
    }

    def throwCustomError() {
        throw new CustomException("Something bad")
    }    
}
