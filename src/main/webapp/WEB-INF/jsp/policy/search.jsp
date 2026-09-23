<%@ page isELIgnored="false" %>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic" %>
<%@ taglib uri="http://northstar.com/tags" prefix="ns" %>
<ns:view path="/WEB-INF/jsp/policy/search.jsp"/>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<table class="page" cellpadding="0" cellspacing="0" border="0">
<tr><td class="banner"><h1><bean:message key="app.title"/></h1></td></tr>
<tr><td class="navigation"><a href="<%= request.getContextPath() %>/policy/list.do"><bean:message key="nav.policies"/></a> |
<a href="<%= request.getContextPath() %>/workbench/list.do"><bean:message key="nav.workbench"/></a> |
<a href="<%= request.getContextPath() %>/report/openByAdjuster.do"><bean:message key="nav.reports"/></a> |
<a href="<%= request.getContextPath() %>/logout.do"><bean:message key="nav.logout"/></a></td></tr>
<tr><td class="content">
<%@ include file="../nav.jsp" %>
<h2><bean:message key="policy.search.heading"/></h2>
<html:form action="/policy/search.do">
<jsp:include page="/WEB-INF/jsp/csrfField.jsp"/>
<table class="form" cellpadding="3" cellspacing="0" border="0">
<tr><td><bean:message key="policy.line"/></td>
<td><html:text property="lineOfBusiness" value="${searchLine}"/></td>
<td><html:submit><bean:message
key="button.search"/></html:submit></td></tr>
</table>
</html:form>
<table class="results" cellpadding="3" cellspacing="0" border="1">
<tr>
<th><bean:message key="policy.number"/></th>
<th><bean:message key="policy.line"/></th>
<th><bean:message
key="policy.insured"/></th>
<th><bean:message key="policy.limit"/></th></tr>
<logic:iterate id="policy" name="policies">
<tr><td><bean:write name="policy" property="policyNumber"/></td>
<td><bean:write name="policy" property="lineOfBusiness"/></td>
<td><bean:write
name="policy" property="insuredName"/></td>
<td><ns:field name="policyLimit_${policy.policyId}" value="${policy.policyLimit}" type="money"/></td></tr>
</logic:iterate>
</table>
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
