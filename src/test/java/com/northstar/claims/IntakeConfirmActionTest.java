package com.northstar.claims;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.struts.action.ActionForward;
import org.apache.struts.action.ActionMapping;
import org.junit.Test;

import com.northstar.claims.web.IntakeConfirmAction;

/** Verifies that the intake confirmation screen cannot reflect markup. */
public class IntakeConfirmActionTest {

    private static final String PAYLOAD =
            "\"><script>alert(document.cookie)</script>";

    /** Resolves forwards without a configured Struts module. */
    private static class MappingStub extends ActionMapping {

        public ActionForward findForward(String name) {
            return new ActionForward(name, "/WEB-INF/jsp/intake/confirm.jsp",
                    false);
        }
    }

    /** Records request attributes for a single claimId parameter value. */
    private static class RequestStub implements InvocationHandler {

        private final String claimId;
        private final Map attributes = new HashMap();

        private RequestStub(String claimId) {
            this.claimId = claimId;
        }

        public Object invoke(Object proxy, Method method, Object[] arguments) {
            String name = method.getName();
            if ("getParameter".equals(name)) {
                return "claimId".equals(arguments[0]) ? claimId : null;
            }
            if ("setAttribute".equals(name)) {
                attributes.put(arguments[0], arguments[1]);
                return null;
            }
            if ("getAttribute".equals(name)) {
                return attributes.get(arguments[0]);
            }
            return null;
        }
    }

    private Map confirm(String claimId) throws Exception {
        RequestStub stub = new RequestStub(claimId);
        HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[] { HttpServletRequest.class }, stub);
        HttpServletResponse response = (HttpServletResponse) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[] { HttpServletResponse.class },
                new RequestStub(null));
        new IntakeConfirmAction().execute(new MappingStub(), null, request,
                response);
        return stub.attributes;
    }

    @Test
    public void numericClaimIdIsReflected() throws Exception {
        Map attributes = confirm("121");
        assertEquals(new Integer(121), attributes.get("claimId"));
        assertEquals("READ_ONLY", attributes.get("confirmationMode"));
    }

    @Test
    public void markupClaimIdIsNotReflected() throws Exception {
        Map attributes = confirm(PAYLOAD);
        assertNull("script payload must not reach the JSP",
                attributes.get("claimId"));
    }

    @Test
    public void nonNumericClaimIdIsNotReflected() throws Exception {
        assertNull(confirm("121abc").get("claimId"));
        assertNull(confirm("").get("claimId"));
        assertNull(confirm(null).get("claimId"));
    }

    @Test
    public void confirmationJspEscapesTheClaimLink() throws Exception {
        String page = read(new File(
                "src/main/webapp/WEB-INF/jsp/intake/confirm.jsp"));
        assertTrue("confirm.jsp must escape the claim link",
                page.indexOf("view.do?claimId=<c:out value='${claimId}'/>") > 0);
        assertFalse("confirm.jsp must not emit claimId raw into the href",
                page.indexOf("view.do?claimId=${claimId}") > 0);
    }

    private String read(File file) throws Exception {
        InputStream input = new FileInputStream(file);
        try {
            byte[] buffer = new byte[(int) file.length()];
            int read = 0;
            while (read < buffer.length) {
                int count = input.read(buffer, read, buffer.length - read);
                if (count < 0) {
                    break;
                }
                read += count;
            }
            return new String(buffer, 0, read, "UTF-8");
        } finally {
            input.close();
        }
    }
}
