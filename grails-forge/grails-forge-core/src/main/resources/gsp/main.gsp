<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8"/>
    <meta name="viewport" content="width=device-width, initial-scale=1"/>
    <title><g:layoutTitle default="Grails"/></title>
    <asset:link rel="icon" href="favicon.ico" type="image/x-ico"/>
    <asset:stylesheet src="application.css"/>
    <g:layoutHead/>
</head>

<body>

<nav class="navbar navbar-expand-lg bg-body border-bottom shadow-sm">
    <div class="container-lg">
        <a class="navbar-brand d-flex align-items-center" href="${request.contextPath}/">
            <asset:image class="w-75" src="grails.svg" alt="Grails Logo"/>
        </a>
    </div>
</nav>

<div class="bg-body-tertiary">
    <div class="container-lg py-4">
        <g:layoutBody/>
    </div>
</div>

<footer class="border-top py-5" role="contentinfo">
    <div class="container-lg">
        <div class="row g-4">
            <div class="col-12 col-md-4">
                <a class="card h-100 text-decoration-none shadow-sm border-1"
                   href="https://guides.grails.org" target="_blank" rel="noopener">
                    <div class="card-body p-4">
                        <div class="d-flex align-items-center justify-content-between mb-2">
                            <h6 class="card-title mb-0 fw-semibold">Grails Guides</h6>
                            <asset:image src="advancedgrails.svg" alt="Grails Guides" width="34" height="34"/>
                        </div>
                        <p class="card-text text-body-secondary mb-0">
                            Building your first Grails app? Looking to add security, or create a Single-Page-App?
                            Check out the Grails Guides for step-by-step tutorials.
                        </p>
                    </div>
                </a>
            </div>
            <div class="col-12 col-md-4">
                <a class="card h-100 text-decoration-none shadow-sm border-1"
                   href="https://grails.apache.org/docs/" target="_blank" rel="noopener">
                    <div class="card-body p-4">
                        <div class="d-flex align-items-center justify-content-between mb-2">
                            <h6 class="card-title mb-0 fw-semibold">Documentation</h6>
                            <asset:image src="documentation.svg" alt="Grails Documentation" width="34" height="34"/>
                        </div>
                        <p class="card-text text-body-secondary mb-0">
                            Ready to dig in? You can find in-depth documentation for all the features
                            of Grails in the User Guide.
                        </p>
                    </div>
                </a>
            </div>
            <div class="col-12 col-md-4">
                <a class="card h-100 text-decoration-none shadow-sm border-1"
                   href="https://grails.apache.org/community.html" target="_blank" rel="noopener">
                    <div class="card-body p-4">
                        <div class="d-flex align-items-center justify-content-between mb-2">
                            <h6 class="card-title mb-0 fw-semibold">Join the Community</h6>
                            <asset:image src="community.svg" alt="Grails Community" width="34" height="34"/>
                        </div>
                        <p class="card-text text-body-secondary mb-0">
                            Get feedback and share your experience with other Grails developers
                            on the Grails mailing lists, GitHub discussions, or the community Slack channel.
                        </p>
                    </div>
                </a>
            </div>
        </div>
    </div>
</footer>
<div id="spinner" class="position-absolute top-0 end-0 p-1" style="display:none;">
    <div class="spinner-border spinner-border-sm" role="status">
        <span class="visually-hidden">Loading...</span>
    </div>
</div>
<asset:javascript src="application.js"/>
</body>
</html>
