package com.hz.crm.agent.web.tool;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.hz.crm.common.time.DateTimes;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CompanyWebSearchService {

    @Value("${crm.agent.web-search.enabled:false}")
    private boolean enabled;

    @Value("${crm.agent.web-search.provider:searxng}")
    private String provider;

    @Value("${crm.agent.web-search.endpoint:}")
    private String endpoint;

    @Value("${crm.agent.web-search.api-key:}")
    private String apiKey;

    @Value("${crm.agent.web-search.timeout-ms:8000}")
    private int timeoutMs;

    @Value("${crm.agent.web-search.max-results:5}")
    private int maxResults;

    @Value("${crm.agent.web-search.fetch-detail:true}")
    private boolean fetchDetail;

    @Value("${crm.agent.web-search.detail-limit:3}")
    private int detailLimit;

    public JSONObject search(String companyName, String keywords, Integer limit) {
        return search(companyName, keywords, limit, null);
    }

    public JSONObject search(String companyName, String keywords, Integer limit, Boolean fetchDetailRequest) {
        JSONObject result = baseResult(companyName, keywords, limit);
        if (!enabled) {
            result.put("available", false);
            result.put("message", "互联网搜索工具未启用");
            return result;
        }
        if (blank(endpoint)) {
            result.put("available", false);
            result.put("message", "互联网搜索服务地址未配置");
            return result;
        }
        try {
            JSONObject remoteResult = callProvider(companyName, keywords, resolveLimit(limit));
            result.put("available", true);
            result.put("message", "搜索完成");
            enrichDetail(result, remoteResult.getJSONArray("results"), resolveFetchDetail(fetchDetailRequest));
            return result;
        } catch (RuntimeException ex) {
            result.put("available", false);
            result.put("message", "互联网搜索失败：" + shrink(ex.getMessage(), 200));
            return result;
        }
    }

    private JSONObject callProvider(String companyName, String keywords, int limit) {
        String providerName = blank(provider) ? "searxng" : provider.trim().toLowerCase();
        if ("tavily".equals(providerName)) {
            return searchTavily(companyName, keywords, limit);
        }
        if ("serper".equals(providerName)) {
            return searchSerper(companyName, keywords, limit);
        }
        return searchSearxng(companyName, keywords, limit);
    }

    private JSONObject searchSearxng(String companyName, String keywords, int limit) {
        String url = trimRightSlash(endpoint) + "/search?q=" + encode(buildQuery(companyName, keywords))
                + "&format=json&language=zh-CN&safesearch=1";
        JSONObject response = requestJson("GET", url, null, null);
        JSONArray sourceResults = response.getJSONArray("results");
        JSONArray results = new JSONArray();
        if (sourceResults != null) {
            for (int i = 0; i < sourceResults.size() && results.size() < limit; i++) {
                JSONObject item = sourceResults.getJSONObject(i);
                JSONObject row = new JSONObject();
                row.put("title", trimToEmpty(item.getString("title")));
                row.put("url", trimToEmpty(item.getString("url")));
                row.put("snippet", trimToEmpty(resolveText(item.getString("content"), item.getString("snippet"))));
                if (!blank(row.getString("title")) || !blank(row.getString("url"))) {
                    results.add(row);
                }
            }
        }
        JSONObject value = new JSONObject();
        value.put("results", results);
        return value;
    }

    private JSONObject searchTavily(String companyName, String keywords, int limit) {
        if (blank(apiKey)) {
            throw new IllegalStateException("Tavily密钥未配置");
        }
        JSONObject body = new JSONObject();
        body.put("api_key", apiKey);
        body.put("query", buildQuery(companyName, keywords));
        body.put("max_results", limit);
        body.put("search_depth", "basic");
        body.put("include_answer", false);
        body.put("include_raw_content", false);
        JSONObject response = requestJson("POST", trimRightSlash(endpoint), body.toJSONString(), "application/json");
        JSONArray sourceResults = response.getJSONArray("results");
        JSONArray results = new JSONArray();
        if (sourceResults != null) {
            for (int i = 0; i < sourceResults.size() && results.size() < limit; i++) {
                JSONObject item = sourceResults.getJSONObject(i);
                JSONObject row = new JSONObject();
                row.put("title", trimToEmpty(item.getString("title")));
                row.put("url", trimToEmpty(item.getString("url")));
                row.put("snippet", trimToEmpty(item.getString("content")));
                if (!blank(row.getString("title")) || !blank(row.getString("url"))) {
                    results.add(row);
                }
            }
        }
        JSONObject value = new JSONObject();
        value.put("results", results);
        return value;
    }

    private JSONObject searchSerper(String companyName, String keywords, int limit) {
        if (blank(apiKey)) {
            throw new IllegalStateException("Serper密钥未配置");
        }
        JSONObject body = new JSONObject();
        body.put("q", buildQuery(companyName, keywords));
        body.put("num", limit);
        JSONObject response = requestJson("POST", trimRightSlash(endpoint), body.toJSONString(), "application/json");
        JSONArray sourceResults = response.getJSONArray("organic");
        JSONArray results = new JSONArray();
        if (sourceResults != null) {
            for (int i = 0; i < sourceResults.size() && results.size() < limit; i++) {
                JSONObject item = sourceResults.getJSONObject(i);
                JSONObject row = new JSONObject();
                row.put("title", trimToEmpty(item.getString("title")));
                row.put("url", trimToEmpty(resolveText(item.getString("link"), item.getString("url"))));
                row.put("snippet", trimToEmpty(item.getString("snippet")));
                if (!blank(row.getString("title")) || !blank(row.getString("url"))) {
                    results.add(row);
                }
            }
        }
        JSONObject value = new JSONObject();
        value.put("results", results);
        return value;
    }

    private JSONObject requestJson(String method, String url, String body, String contentType) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setRequestProperty("Accept", "application/json");
            if ("serper".equalsIgnoreCase(provider) && !blank(apiKey)) {
                connection.setRequestProperty("X-API-KEY", apiKey);
            }
            if (!blank(contentType)) {
                connection.setRequestProperty("Content-Type", contentType);
            }
            if (!blank(body)) {
                connection.setDoOutput(true);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
                OutputStream outputStream = connection.getOutputStream();
                try {
                    outputStream.write(bytes);
                } finally {
                    outputStream.close();
                }
            }
            int status = connection.getResponseCode();
            String text = readText(status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream());
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("搜索服务响应异常：" + status + " " + shrink(text, 120));
            }
            return JSON.parseObject(text);
        } catch (IOException ex) {
            throw new IllegalStateException(ex.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void enrichDetail(JSONObject result, JSONArray results, boolean shouldFetchDetail) {
        JSONArray safeResults = results == null ? new JSONArray() : results;
        JSONObject profileDraft = emptyProfileDraft(result.getString("companyName"), result.getString("searchedAt"));
        if (shouldFetchDetail) {
            int maxDetailCount = resolveDetailLimit(safeResults.size());
            for (int i = 0; i < safeResults.size(); i++) {
                JSONObject item = safeResults.getJSONObject(i);
                JSONObject detail = i < maxDetailCount
                        ? fetchAndExtractDetail(item.getString("url"))
                        : skippedDetail(item.getString("url"));
                item.put("detail", detail);
                mergeProfile(profileDraft, detail);
            }
        }
        result.put("results", safeResults);
        result.put("profileDraft", profileDraft);
    }

    private boolean resolveFetchDetail(Boolean fetchDetailRequest) {
        if (fetchDetailRequest == null) {
            return fetchDetail;
        }
        return fetchDetailRequest.booleanValue();
    }

    private int resolveDetailLimit(int resultSize) {
        int value = detailLimit <= 0 ? 3 : detailLimit;
        if (value > 5) {
            value = 5;
        }
        return Math.min(value, resultSize);
    }

    private JSONObject skippedDetail(String pageUrl) {
        JSONObject detail = new JSONObject();
        detail.put("url", trimToEmpty(pageUrl));
        detail.put("available", false);
        detail.put("message", "超过详情抓取数量限制，未抓取正文");
        detail.put("text", "");
        detail.put("fields", emptyProfileDraft("", ""));
        return detail;
    }

    private JSONObject fetchAndExtractDetail(String pageUrl) {
        JSONObject detail = new JSONObject();
        detail.put("url", trimToEmpty(pageUrl));
        detail.put("available", false);
        detail.put("message", "");
        detail.put("text", "");
        detail.put("fields", emptyProfileDraft("", ""));
        if (blank(pageUrl) || !isHttpUrl(pageUrl)) {
            detail.put("message", "详情地址无效");
            return detail;
        }
        try {
            String html = requestText(pageUrl);
            String text = extractPlainText(html);
            JSONObject fields = extractCompanyFields(text);
            detail.put("available", !blank(text));
            detail.put("message", blank(text) ? "未提取到详情正文" : "详情抓取完成");
            detail.put("text", shrink(text, 1800));
            detail.put("fields", fields);
            return detail;
        } catch (RuntimeException ex) {
            detail.put("message", "详情抓取失败：" + shrink(ex.getMessage(), 160));
            return detail;
        }
    }

    private String requestText(String pageUrl) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(pageUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(timeoutMs);
            connection.setReadTimeout(timeoutMs);
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 AppleWebKit/537.36 Chrome Safari");
            int status = connection.getResponseCode();
            String text = readText(status >= 200 && status < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream());
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("详情页响应异常：" + status + " " + shrink(text, 80));
            }
            return text;
        } catch (IOException ex) {
            throw new IllegalStateException(ex.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private JSONObject extractCompanyFields(String text) {
        JSONObject fields = emptyProfileDraft("", "");
        String normalized = normalizePlainText(text);
        fields.put("legalRepresentative", firstMatch(normalized, "法定代表人", "法人代表", "法人", "负责人"));
        fields.put("keyPerson", firstMatch(normalized, "主要人员", "高管", "联系人"));
        fields.put("companyScale", firstMatch(normalized, "人员规模", "企业规模", "公司规模", "参保人数"));
        fields.put("industry", firstMatch(normalized, "所属行业", "行业", "经营范围"));
        fields.put("phone", firstPhone(normalized));
        fields.put("email", firstEmail(normalized));
        fields.put("website", firstWebsite(normalized));
        fields.put("address", firstMatch(normalized, "注册地址", "企业地址", "地址", "通信地址"));
        fields.put("registeredCapital", firstMatch(normalized, "注册资本", "注册资金"));
        fields.put("sourceSummary", shrink(normalized, 500));
        return fields;
    }

    private String extractPlainText(String html) {
        if (blank(html)) {
            return "";
        }
        String text = html;
        text = text.replaceAll("(?is)<script[^>]*>.*?</script>", " ");
        text = text.replaceAll("(?is)<style[^>]*>.*?</style>", " ");
        text = text.replaceAll("(?is)<noscript[^>]*>.*?</noscript>", " ");
        text = text.replaceAll("(?is)<[^>]+>", " ");
        text = decodeHtml(text);
        return normalizePlainText(text);
    }

    private String normalizePlainText(String value) {
        if (blank(value)) {
            return "";
        }
        String text = value.replace('\u00A0', ' ');
        text = text.replaceAll("[\\t\\r\\n]+", " ");
        text = text.replaceAll("\\s{2,}", " ");
        return text.trim();
    }

    private String firstMatch(String text, String... labels) {
        if (blank(text) || labels == null) {
            return "";
        }
        for (String label : labels) {
            String value = matchLabelValue(text, label);
            if (!blank(value)) {
                return shrink(cleanFieldValue(value), 160);
            }
        }
        return "";
    }

    private String matchLabelValue(String text, String label) {
        Pattern pattern = Pattern.compile(Pattern.quote(label)
                + "\\s*[:：]?\\s*([^|,，;；。\\s][^|;；。]{0,100})");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private String firstPhone(String text) {
        String value = firstMatch(text, "电话", "联系方式", "联系电话");
        if (!blank(value)) {
            Matcher matcher = Pattern.compile("(?:\\+?86[-\\s]?)?(?:1\\d{10}|0\\d{2,3}[-\\s]?\\d{7,8})").matcher(value);
            if (matcher.find()) {
                return matcher.group();
            }
            return shrink(cleanFieldValue(value), 80);
        }
        Matcher matcher = Pattern.compile("(?:\\+?86[-\\s]?)?(?:1\\d{10}|0\\d{2,3}[-\\s]?\\d{7,8})").matcher(text);
        return matcher.find() ? matcher.group() : "";
    }

    private String firstEmail(String text) {
        String value = firstMatch(text, "邮箱", "电子邮箱", "Email");
        Matcher matcher = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").matcher(
                blank(value) ? text : value);
        return matcher.find() ? matcher.group() : "";
    }

    private String firstWebsite(String text) {
        String value = firstMatch(text, "官网", "网站", "网址");
        Matcher matcher = Pattern.compile("https?://[^\\s，。；;]+|www\\.[^\\s，。；;]+").matcher(blank(value) ? text : value);
        return matcher.find() ? matcher.group() : shrink(cleanFieldValue(value), 160);
    }

    private String cleanFieldValue(String value) {
        if (blank(value)) {
            return "";
        }
        String text = value.trim();
        text = text.replaceAll("^(：|:)", "").trim();
        text = text.replaceAll("(查看|复制|展开|更多)$", "").trim();
        return text;
    }

    private JSONObject emptyProfileDraft(String companyName, String searchedAt) {
        JSONObject profile = new JSONObject();
        profile.put("available", false);
        profile.put("companyName", trimToEmpty(companyName));
        profile.put("legalRepresentative", "");
        profile.put("keyPerson", "");
        profile.put("companyScale", "");
        profile.put("industry", "");
        profile.put("phone", "");
        profile.put("email", "");
        profile.put("website", "");
        profile.put("address", "");
        profile.put("registeredCapital", "");
        profile.put("sourceSummary", "");
        profile.put("searchedAt", trimToEmpty(searchedAt));
        profile.put("sourceUrls", new JSONArray());
        return profile;
    }

    private void mergeProfile(JSONObject target, JSONObject detail) {
        if (target == null || detail == null || !detail.getBooleanValue("available")) {
            return;
        }
        JSONObject fields = detail.getJSONObject("fields");
        if (fields == null) {
            return;
        }
        target.put("available", true);
        mergeText(target, fields, "legalRepresentative");
        mergeText(target, fields, "keyPerson");
        mergeText(target, fields, "companyScale");
        mergeText(target, fields, "industry");
        mergeText(target, fields, "phone");
        mergeText(target, fields, "email");
        mergeText(target, fields, "website");
        mergeText(target, fields, "address");
        mergeText(target, fields, "registeredCapital");
        mergeText(target, fields, "sourceSummary");
        JSONArray urls = target.getJSONArray("sourceUrls");
        String url = detail.getString("url");
        if (!blank(url) && !urls.contains(url)) {
            urls.add(url);
        }
    }

    private void mergeText(JSONObject target, JSONObject source, String key) {
        if (blank(target.getString(key)) && !blank(source.getString(key))) {
            target.put(key, source.getString(key));
        }
    }

    private String decodeHtml(String value) {
        if (blank(value)) {
            return "";
        }
        return value
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'");
    }

    private boolean isHttpUrl(String value) {
        String text = trimToEmpty(value).toLowerCase();
        return text.startsWith("http://") || text.startsWith("https://");
    }

    private JSONObject baseResult(String companyName, String keywords, Integer limit) {
        JSONObject result = new JSONObject();
        result.put("available", false);
        result.put("provider", trimToEmpty(provider));
        result.put("query", buildQuery(companyName, keywords));
        result.put("companyName", trimToEmpty(companyName));
        result.put("searchedAt", DateTimes.now().toString());
        result.put("limit", resolveLimit(limit));
        result.put("results", new JSONArray());
        return result;
    }

    private String buildQuery(String companyName, String keywords) {
        StringBuilder builder = new StringBuilder();
        builder.append(trimToEmpty(companyName));
        builder.append(" 公司 负责人 公司规模 行业 电话 官网 地址 工商");
        if (!blank(keywords)) {
            builder.append(' ').append(keywords.trim());
        }
        return builder.toString().trim();
    }

    private int resolveLimit(Integer limit) {
        int configured = maxResults <= 0 ? 5 : maxResults;
        int value = limit == null || limit.intValue() <= 0 ? configured : limit.intValue();
        if (value > configured) {
            value = configured;
        }
        if (value > 10) {
            value = 10;
        }
        return Math.max(value, 1);
    }

    private String readText(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        try {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(line);
            }
            return builder.toString();
        } finally {
            reader.close();
        }
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (IOException ex) {
            return value;
        }
    }

    private String trimRightSlash(String value) {
        if (blank(value)) {
            return "";
        }
        String text = value.trim();
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private String resolveText(String first, String second) {
        if (!blank(first)) {
            return first;
        }
        if (!blank(second)) {
            return second;
        }
        return "";
    }

    private String trimToEmpty(String value) {
        if (blank(value)) {
            return "";
        }
        return value.trim();
    }

    private String shrink(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private boolean blank(String value) {
        return value == null || value.trim().length() == 0;
    }
}
