package functionaltests

/**
 */
class TestFilters {

    /**
     * This tests that adding a filter to an action that performs a forward doesn't break the action
     */
    def filters = {
        myFilter(controller:'forwarding', action:"forwardToList") {
            before = {
                return true
            }
        }
    }
}
