<%=packageName ? "package ${packageName}" : ''%>

import grails.validation.ValidationException
import static org.springframework.http.HttpStatus.CREATED
import static org.springframework.http.HttpStatus.NOT_FOUND
import static org.springframework.http.HttpStatus.NO_CONTENT
import static org.springframework.http.HttpStatus.OK
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY

import grails.gorm.transactions.ReadOnly
import grails.gorm.transactions.Transactional

@ReadOnly
class ${className}Controller {

    ${className}Service ${propertyName}Service

    static responseFormats = ['json', 'xml']
    static allowedMethods = [save: "POST", update: "PUT", delete: "DELETE"]

    def index(Integer max) {
        params.max = Math.min(max ?: 10, 100)
        respond ${propertyName}Service.list(params), model:[${propertyName}Count: ${propertyName}Service.count()]
    }

    def show(Serializable id) {
        respond ${propertyName}Service.get(id)
    }

    @Transactional
    def save(${className} ${propertyName}) {
        if (${propertyName} == null) {
            render status: NOT_FOUND
            return
        }
        if (${propertyName}.hasErrors()) {
            transactionStatus.setRollbackOnly()
            respond ${propertyName}.errors
            return
        }

        try {
            ${propertyName}Service.save(${propertyName})
        } catch (ValidationException e) {
            respond ${propertyName}.errors
            return
        }

        respond ${propertyName}, [status: CREATED, view:"show"]
    }

    @Transactional
    def update(${className} ${propertyName}) {
        if (${propertyName} == null) {
            render status: NOT_FOUND
            return
        }
        if (${propertyName}.hasErrors()) {
            transactionStatus.setRollbackOnly()
            respond ${propertyName}.errors
            return
        }

        try {
            ${propertyName}Service.save(${propertyName})
        } catch (ValidationException e) {
            respond ${propertyName}.errors
            return
        }

        respond ${propertyName}, [status: OK, view:"show"]
    }

    @Transactional
    def delete(Serializable id) {
        if (id == null || ${propertyName}Service.delete(id) == null) {
            render status: NOT_FOUND
            return
        }

        render status: NO_CONTENT
    }
}
