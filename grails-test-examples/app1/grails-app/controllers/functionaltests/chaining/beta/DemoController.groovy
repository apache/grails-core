package functionaltests.chaining.beta

class DemoController {
    static namespace = 'beta'

    def doit() {
        render 'rendered from the Demo controller in the beta namespace'
    }
}
