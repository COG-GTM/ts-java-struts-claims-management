<%@ page isELIgnored="false" %><%@ taglib
 uri="http://struts.apache.org/tags-bean" prefix="bean" %><input
 type="hidden" name="csrfToken" id="csrfToken" value="<bean:write
 name="csrfToken" scope="session" ignore="true" filter="true"/>"/>
