# Admin area

## Mapping and security

The servlet config/admin parameter mounts struts-config-admin.xml at /admin. *.do requests pass AuthFilter; there are no security-constraints or admin role checks. Modern shared security already provides login and operator authentication. AdminRedirectFilter only preserves Jetty's absolute login Location for /admin/ requests, leaving shared security unchanged.

| URL | Action/modern handler | Actual authenticated response |
|---|---|---|
| /claims/admin/adjusters.do | AdjusterAdminAction / AdminController.adjusters | 404, /WEB-INF/jsp/error.jsp |
| /claims/admin/reference.do | ReferenceDataAction / AdminController.reference | 404, /WEB-INF/jsp/error.jsp |
| /claims/admin/editAdjuster.do | No legacy mapping / AdminController.unmappedEdit | 500, /WEB-INF/jsp/error.jsp |

GET and POST have identical behavior. Parameters do not select a dispatch method. Unauthenticated GET and POST redirect with 302 to the absolute context login URL, including the origin port.

## Quirks retained, not fixed

* **Both configured admin screens actually fail with 404.** Struts module-relative forwards resolve /WEB-INF/jsp/admin/*.jsp under /admin, but those resources do not exist at /admin/WEB-INF/jsp/admin/*.jsp. The converted screen templates remain present but unreachable, rather than silently repairing these URLs.
* Evidence: live requests to both mapped actions returned 404 with the error.jsp view marker; a reference request with method/dispatch/prefix parameters still returned 404, ruling out dispatch selection. Struts 1.3.10 PerformForward bytecode resolves leading-slash forwards through resolveModuleRelativePath and RequestUtils.forwardURL. The runtime log records initialization of the /admin processor and AdjusterAdminAction. No legacy source edits or module overrides were used.
* The admin descriptor names adjusterForm but declares no form-beans. Runtime logs say No FormBeanConfig found in module /admin under name adjusterForm. The action still runs with no form and does not use submitted fields. No validation or mutations are added.
* The adjuster query and adjusterCount remain even though the static screen ignores them. The existing injected AdjusterDAO is reused unchanged; no new repository is necessary.
* The converted adjuster table still has exactly one hardcoded adjuster1 field, sample-name and sample-region resource keys. Reference fields retain AUTO, HOMEOWNERS, COMMERCIAL_PROPERTY and GENERAL_LIABILITY. No query-driven replacement rows.
* ReferenceDataAction's unused referenceVersion=2019.04 and lossTypes=WATER,FIRE,THEFT,COLLISION remain.
* The editAdjuster forward is never selected. Its template is converted, including fullName/region/active inputs and save form, but no success route exposes it. Its relative editAdjuster.do link is retained in the converted adjuster screen. The unmapped URL throws InvalidPathException in legacy (runtime stack trace), resulting in 500, not 404.
* All converted templates preserve original JSP path markers and message keys. HTTP failure pages have no business fields or ns:error markers. Legacy error status text is retained through the area-local admin/error.html template.

## Decisions and scope

No shared Wave 0 file, legacy file, harness, snapshot or PARITY.md was changed. New production files are exclusively in web.admin and templates/admin. The extra area-local error template avoids changing the shared error template's null exception reference. An area-local response wrapper changes only the login Location header on admin requests to Jetty's absolute form; it does not change access control.

AdminIntegrationTest uses MockMvc with real Boot security and Thymeleaf plus a mock AdjusterDAO to cover nonempty/empty query results, query exceptions, GET/POST, ignored form/dispatch inputs, role-free operator access, and anonymous redirects. Exceptions from the query still use the existing HTTP 200 shared exception forward. Real storage and supervisor login were separately exercised on both running apps by curl; see admin-parity.md.

## Commands

* JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 make build; make seed; make run (apply JAVA_HOME to each legacy command).
* JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 make -C modernized build; make -C modernized seed; make -C modernized run.
* make -C modernized test.
* make -C modernized replay AREA=admin fails because admin is not a supported replay choice; exact output is in admin-parity.md.

Maven environment repair: initial central downloads contained 96-byte rate-limit responses and checksum failures. Outside the repo, ~/.m2/settings.xml was configured with the provisioned MAVEN_MIRROR_URL (Google's Maven Central mirror), and only corrupt 96-byte cache files were removed. Legacy's documented duplicate JSTL scanning and JNDI fallback warnings remain unchanged.
