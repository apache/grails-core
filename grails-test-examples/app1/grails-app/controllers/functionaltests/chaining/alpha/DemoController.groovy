package functionaltests.chaining.alpha

class DemoController {
    static namespace = 'alpha'

    def doit() {
        render 'rendered from the Demo controller in the alpha namespace'
    }
}
