package functionaltests

class IncludesController {

    def viewRendering() {
        [foo:"bar"]
    }

    def textRendering() {
        render "Test"
    }

    def includeFromControllerActionRenderingView() {
        render g.include(controller:"includes", action:"viewRendering")
    }

    def includeFromControllerActionRenderingText() {
        render g.include(controller:"includes", action:"textRendering")
    }

    def includeFromViewRenderingView() {
        render view:"viewInclude"
    }

    def includeFromViewRenderingText() {
        render view:"textInclude"
    }

    def includeFromTemplateRenderingView() {
        render template:"viewInclude"
    }

    def includeFromTemplateRenderingText() {
        render template:"textInclude"
    }
}
