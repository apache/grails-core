<%=packageName ? "package ${packageName}" : ''%>

import grails.gorm.services.Service

@Service(${className})
interface ${className}Service {

    ${className} get(Serializable id)

    List<${className}> list(Map args)

    Long count()

    void delete(Serializable id)

    ${className} save(${className} ${propertyName})

}