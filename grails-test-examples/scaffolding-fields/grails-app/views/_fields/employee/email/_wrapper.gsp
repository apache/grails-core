<%--
    Custom wrapper template for Employee.email field
    Tests that custom templates can add validation hints
--%>
<div class="form-group custom-email-wrapper" data-field="email">
    <label for="${property}" class="custom-label">
        <g:if test="${required}"><span class="required-marker">*</span></g:if>
        ${label}
    </label>
    <div class="input-group">
        <span class="input-group-text custom-icon">@</span>
        ${raw(widget)}
    </div>
    <g:if test="${invalid}">
        <div class="custom-validation-error">
            <g:each var="error" in="${errors}">
                <span class="field-error">${error}</span>
            </g:each>
        </div>
    </g:if>
</div>
