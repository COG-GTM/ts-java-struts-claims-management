<%@ page isELIgnored="false" %><input type="hidden" name="csrfToken"
 id="csrfToken" value="<%= session.getAttribute("csrfToken") == null ? ""
 : session.getAttribute("csrfToken") %>"/>
