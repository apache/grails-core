package functionaltests.chaining

class ChainingHomeController {

    def chainDemo(String ns) {
        chain controller: 'demo', action: 'doit', namespace: ns
    }
}
