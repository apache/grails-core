grails.gorm.default.constraints = {
    nonEmpty(validator: { val, obj ->
        val != null && val.length() > 0
    })
}