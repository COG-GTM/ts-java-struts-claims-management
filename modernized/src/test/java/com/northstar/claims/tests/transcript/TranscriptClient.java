package com.northstar.claims.tests.transcript;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Java port of the request, extraction and probe logic in tools/capture/capture.py.
 *
 * Only what the harness compares is produced: status, result, business fields,
 * validation errors and db_state probes. Regexes and fallbacks are copied verbatim so a
 * green test here means the Python harness would extract the same values.
 */
public class TranscriptClient {
    // capture.py FIELD_RE / VIEW_RE / ERROR_RE (re.S on FIELD_RE).
    private static final Pattern FIELD_RE = Pattern.compile("<span id=\"f_([^\"]+)\">(.*?)</span>", Pattern.DOTALL);
    private static final Pattern VIEW_RE = Pattern.compile("<!--\\s*ns:view\\s+([^ ]+)\\s*-->");
    private static final Pattern ERROR_RE = Pattern.compile("<!--\\s*ns:error\\s+([^ ]+)\\s*-->");
    // Python's \s is Unicode-aware for str patterns.
    private static final Pattern WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS);

    /** One HTTP exchange after redirects have been followed. */
    public record Response(int status, String location, String body) {}

    private final String origin;
    private final HttpClient http;

    /** @param origin scheme, host and port; transcript paths already include /claims. */
    public TranscriptClient(String origin) {
        this.origin = origin;
        // A cookie jar keeps JSESSIONID like urllib's HTTPCookieProcessor; the default
        // urllib opener follows redirects, so the final response is what capture records.
        this.http = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    /** capture.request(): form-encoded body only when the form is non-empty. */
    public Response request(String method, String path, Map<String, String> form) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(origin + path))
                .timeout(Duration.ofSeconds(20));
        if (form != null && !form.isEmpty()) {
            String body = form.entrySet().stream()
                    .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                    .collect(Collectors.joining("&"));
            builder.header("Content-Type", "application/x-www-form-urlencoded")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        HttpResponse<String> response = http.send(builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Response(response.statusCode(),
                response.headers().firstValue("Location").orElse(response.uri().toString()),
                response.body());
    }

    /** parity/common.py opener(): visit the login page, then optionally sign in as supervisor. */
    public TranscriptClient openSession(boolean login) throws Exception {
        request("GET", "/claims/login.do", Map.of());
        if (login) {
            Map<String, String> credentials = new LinkedHashMap<>();
            credentials.put("username", "supervisor");
            credentials.put("password", "supervisor");
            request("POST", "/claims/login.do", credentials);
        }
        return this;
    }

    /** capture.fields(): whitespace collapsed, empty values dropped, keys sorted. */
    public static Map<String, String> fields(String body) {
        Map<String, String> values = new TreeMap<>();
        Matcher matcher = FIELD_RE.matcher(body);
        while (matcher.find()) {
            String value = WHITESPACE.matcher(matcher.group(2)).replaceAll(" ").strip();
            if (!value.isEmpty()) {
                values.put(matcher.group(1), value);
            }
        }
        return values;
    }

    /** capture.result(): redirect, view marker, system error, else the login page. */
    public static String result(String body, int code, String location) {
        if ((code == 301 || code == 302 || code == 303 || code == 307 || code == 308) && location != null) {
            return "redirect:" + location.replaceAll(";jsessionid=[^?]+", "");
        }
        Matcher match = VIEW_RE.matcher(body);
        if (match.find()) {
            return "forward:" + match.group(1);
        }
        if (body.contains("Claims System Error")) {
            return "error:errors.system";
        }
        return "forward:/WEB-INF/jsp/login.jsp";
    }

    /** capture's ERROR_RE.findall(): resource keys in page order. */
    public static List<String> validationErrors(String body) {
        List<String> keys = new ArrayList<>();
        Matcher matcher = ERROR_RE.matcher(body);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    /**
     * Read page for a probe key, from the vocabulary in transcripts/README.md. These are
     * exactly the paths capture.py SCENARIOS pair with each committed probe key.
     */
    public static String probePath(String key) {
        String[] parts = key.split("\\.");
        if (key.startsWith("payment.")) {
            return "/claims/payment/history.do";
        }
        if (key.startsWith("settlement.claim.")) {
            return "/claims/settlement/calculate.do?claimId=" + parts[2];
        }
        if (key.startsWith("claim.")) {
            return "/claims/workbench/view.do?claimId=" + parts[1];
        }
        if (key.startsWith("policy.")) {
            return "/claims/policy/view.do?policyId=" + parts[1];
        }
        throw new IllegalArgumentException("Unsupported probe key " + key);
    }

    /** capture.probe(): the elif chain is order-sensitive and is kept in the same order. */
    public String probe(String key) throws Exception {
        Map<String, String> values = fields(request("GET", probePath(key), Map.of()).body());
        if (key.startsWith("payment.count.claim.")) {
            return String.valueOf(values.keySet().stream().filter(n -> n.startsWith("paymentAmount_")).count());
        }
        if (key.startsWith("payment.") && key.chars().filter(c -> c == '.').count() >= 2) {
            String[] parts = key.split("\\.");
            return values.getOrDefault("payment" + pythonTitle(parts[2]) + "_" + parts[1], "");
        }
        if (key.endsWith(".status")) {
            return values.getOrDefault("claimStatus", values.getOrDefault("paymentStatus",
                    values.getOrDefault("policyStatus", "")));
        }
        if (key.endsWith(".assigned_adjuster")) {
            return values.getOrDefault("assignedAdjuster", "");
        }
        if (key.endsWith(".reserve_amount")) {
            return values.getOrDefault("reserveAmount", "");
        }
        if (key.endsWith(".exists")) {
            return values.isEmpty() ? "false" : "true";
        }
        if (key.endsWith(".loss_date")) {
            return values.getOrDefault("lossDate", "");
        }
        if (key.contains(".limit")) {
            return values.getOrDefault("policyLimit", "");
        }
        if (key.startsWith("settlement.claim.")) {
            return values.getOrDefault("settlementAmount", "");
        }
        return "";
    }

    /** Python str.title(): upper-case the first letter of each alphabetic run, lower-case the rest. */
    static String pythonTitle(String text) {
        StringBuilder out = new StringBuilder();
        boolean previousCased = false;
        for (char c : text.toCharArray()) {
            out.append(previousCased ? Character.toLowerCase(c) : Character.toUpperCase(c));
            previousCased = Character.isLetter(c);
        }
        return out.toString();
    }

    private static String encode(String value) {
        // urllib.parse.urlencode uses quote_plus: spaces become '+', like URLEncoder.
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
