<%@ page isELIgnored="false" %>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic" %>
<%@ taglib uri="http://northstar.com/tags" prefix="ns" %>
<ns:view path="/WEB-INF/jsp/intake/new.jsp"/>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<table class="page" cellpadding="0" cellspacing="0" border="0">
<tr><td class="banner"><h1><bean:message key="app.title"/></h1></td></tr>
<tr><td class="navigation"><a href="<%= request.getContextPath() %>/policy/list.do"><bean:message key="nav.policies"/></a> |
<a href="<%= request.getContextPath() %>/workbench/list.do"><bean:message key="nav.workbench"/></a> |
<a href="<%= request.getContextPath() %>/report/openByAdjuster.do"><bean:message key="nav.reports"/></a> |
<a href="<%= request.getContextPath() %>/logout.do"><bean:message key="nav.logout"/></a></td></tr>
<tr><td class="content">
<%@ include file="../nav.jsp" %>
<h2><bean:message key="intake.heading"/></h2>
<form action="intake/submit.do" method="post">
<jsp:include page="/WEB-INF/jsp/csrfField.jsp"/>
<%
java.util.List nsValidationErrors =
        (java.util.List) request.getAttribute("nsValidationErrors");
if (nsValidationErrors != null) {
    java.util.Iterator nsMessages = nsValidationErrors.iterator();
    while (nsMessages.hasNext()) {
%><!-- ns:error <%= nsMessages.next() %> --><%
    }
}
%>
<table class="form" cellpadding="3" cellspacing="0" border="0">
<tr><td><bean:message key="intake.claimant"/></td>
<td><input type="text" name="claimantName" size="40"/></td></tr>
<tr><td><bean:message key="intake.lossDate"/></td>
<td><input type="text" name="lossDate" size="20"/></td></tr>
<tr><td><bean:message key="intake.description"/></td>
<td><textarea name="description" rows="5" cols="45"></textarea></td></tr>
<tr><td><bean:message key="intake.lossType"/></td>
<td><input type="text" name="lossType" size="20"/></td></tr>
<tr><td>&nbsp;</td>
<td><button type="submit"><bean:message key="button.submit"/></button></td></tr>
</table>
</form>
<p><bean:message key="intake.instruction"/></p>
<table class="screen-audit" cellpadding="3" cellspacing="0" border="1">
<tr>
<th><bean:message key="jsp.screen.context"/></th>
<td><bean:message key="jsp.screen.readOnly"/></td>
</tr>
<tr>
<th><bean:message key="jsp.screen.operator"/></th>
<td><bean:write name="user"/></td>
</tr>
<tr>
<th><bean:message key="jsp.screen.asOf"/></th>
<td><%= com.northstar.claims.util.ClaimsConfig.get("report.asof.date") %></td>
</tr>
</table>
<%@ include file="../footer.jsp" %>
