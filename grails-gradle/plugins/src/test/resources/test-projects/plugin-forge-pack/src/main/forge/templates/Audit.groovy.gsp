package ${project.packageName}

class ${project.className}Audit {
<% if (features.contains('gsp')) { %>
    boolean views = true
<% } %>
}
