class DemoFilters {
    def filters = {
        allFilters(controller: 'filtersDemo') {
            before  = {
                params.filterRan = 'yes'
                //true
            }
        }

        dataFilter(controller: 'filtersDemo', action: 'data') {
            after = { Map model ->
                model.companyCity = 'St. Louis'
            }
        }
    }
}
