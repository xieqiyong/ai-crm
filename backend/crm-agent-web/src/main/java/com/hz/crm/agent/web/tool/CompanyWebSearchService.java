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
            prioritizeCompanyDetail(remoteResult.getJSONArray("results"));
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
            JSONObject fields = extractStructuredCompanyFields(html);
            mergeProfileFields(fields, extractCompanyFields(text));
            detail.put("available", hasProfileFields(fields));
            detail.put("message", resolveDetailMessage(html, text, fields));
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
            connection.setInstanceFollowRedirects(true);
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

    private void prioritizeCompanyDetail(JSONArray results) {
        if (results == null || results.isEmpty()) {
            return;
        }
        JSONArray preferred = new JSONArray();
        JSONArray others = new JSONArray();
        for (int i = 0; i < results.size(); i++) {
            JSONObject item = results.getJSONObject(i);
            if (isPreferredCompanyPage(item)) {
                preferred.add(item);
            } else {
                others.add(item);
            }
        }
        results.clear();
        results.addAll(preferred);
        results.addAll(others);
    }

    private boolean isPreferredCompanyPage(JSONObject item) {
        if (item == null) {
            return false;
        }
        String url = trimToEmpty(item.getString("url")).toLowerCase();
        String title = trimToEmpty(item.getString("title"));
        if (url.contains("aiqicha.baidu.com/company_detail_")
                || url.contains("aiqicha.baidu.com/detail/compinfo")) {
            return true;
        }
        return title.contains("爱企查");
    }

    private JSONObject extractCompanyFields(String text) {
        JSONObject fields = emptyProfileDraft("", "");
        String normalized = normalizePlainText(text);
        fields.put("creditCode", firstMatch(normalized, "统一社会信用代码", "社会信用代码", "信用代码"));
        fields.put("legalRepresentative", firstMatch(normalized, "法定代表人", "法人代表", "法人", "负责人"));
        fields.put("keyPerson", firstMatch(normalized, "主要人员", "高管", "联系人"));
        fields.put("companyScale", firstMatch(normalized, "人员规模", "企业规模", "公司规模", "参保人数"));
        fields.put("industry", firstMatch(normalized, "所属行业", "行业", "经营范围"));
        fields.put("phone", firstPhone(normalized));
        fields.put("email", firstEmail(normalized));
        fields.put("website", firstWebsite(normalized));
        fields.put("address", firstMatch(normalized, "注册地址", "企业地址", "地址", "通信地址"));
        fields.put("registeredCapital", firstMatch(normalized, "注册资本", "注册资金"));
        fields.put("establishDate", firstMatch(normalized, "注册时间", "成立日期", "成立时间", "注册日期"));
        fields.put("description", firstMatch(normalized, "简介", "公司简介", "企业简介"));
        fields.put("sourceSummary", buildTextSummary(fields, normalized));
        return fields;
    }

    private JSONObject extractStructuredCompanyFields(String html) {
        JSONObject fields = emptyProfileDraft("", "");
        if (blank(html)) {
            return fields;
        }
        String source = decodeUnicodeEscapes(decodeHtml(html));
        fillField(fields, "companyName", firstJsonValue(source,
                "entName", "companyName", "companyFullName", "name", "title"));
        fillField(fields, "creditCode", firstJsonValue(source,
                "creditCode", "unifiedSocialCreditCode", "socialCreditCode", "regNo", "taxNo"));
        fillField(fields, "legalRepresentative", firstJsonObjectName(source,
                "legalPerson", "legalPersonInfo", "oper"));
        fillField(fields, "legalRepresentative", firstJsonValue(source,
                "legalPerson", "legalPersonName", "legalRepresentative", "operName", "frName"));
        fillField(fields, "keyPerson", firstJsonValue(source,
                "contactName", "mainManager", "keyPerson", "personName"));
        fillField(fields, "companyScale", firstJsonValue(source,
                "staffNum", "insuredNum", "socialSecurityStaffNum", "companyScale", "scale"));
        fillField(fields, "industry", firstJsonValue(source,
                "industry", "industryName", "industryPhyName", "category"));
        fillField(fields, "phone", firstPhone(firstJsonValue(source,
                "phone", "telephone", "phoneNumber", "contactPhone")));
        fillField(fields, "email", firstEmail(firstJsonValue(source,
                "email", "emailAddress")));
        fillField(fields, "website", firstWebsite(firstJsonValue(source,
                "website", "webSite", "site", "homepage")));
        fillField(fields, "address", firstJsonValue(source,
                "regAddr", "address", "registeredAddress", "dom", "officeAddress"));
        fillField(fields, "registeredCapital", firstJsonValue(source,
                "regCapital", "registeredCapital", "regCap"));
        fillField(fields, "establishDate", firstJsonValue(source,
                "startDate", "estiblishTime", "establishDate", "foundDate"));
        fillField(fields, "description", firstJsonValue(source,
                "description", "summary", "brief", "companyDesc"));
        fillField(fields, "sourceSummary", buildStructuredSummary(source, fields));
        mergeProfileFields(fields, extractCompanyFields(extractMetaText(html)));
        return fields;
    }

    private String firstJsonValue(String source, String... keys) {
        if (blank(source) || keys == null) {
            return "";
        }
        for (String key : keys) {
            String value = matchJsonString(source, key);
            if (!blank(value)) {
                return shrink(cleanFieldValue(value), 180);
            }
            value = matchJsonNumber(source, key);
            if (!blank(value)) {
                return shrink(cleanFieldValue(value), 80);
            }
        }
        return "";
    }

    private String firstJsonObjectName(String source, String... keys) {
        if (blank(source) || keys == null) {
            return "";
        }
        for (String key : keys) {
            Pattern pattern = Pattern.compile("\"" + Pattern.quote(key)
                    + "\"\\s*:\\s*\\{[^{}]{0,800}?\"name\"\\s*:\\s*\"([^\"]{1,180})\"");
            Matcher matcher = pattern.matcher(source);
            if (matcher.find()) {
                return shrink(cleanFieldValue(matcher.group(1)), 180);
            }
        }
        return "";
    }

    private String matchJsonString(String source, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key)
                + "\"\\s*:\\s*\"([^\"]{1,500})\"");
        Matcher matcher = pattern.matcher(source);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private String matchJsonNumber(String source, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key)
                + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
        Matcher matcher = pattern.matcher(source);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private String buildStructuredSummary(String source, JSONObject fields) {
        String scope = firstJsonValue(source, "businessScope", "scope", "operateScope");
        String status = firstJsonValue(source, "regStatus", "status", "openStatus");
        String startDate = firstJsonValue(source, "startDate", "estiblishTime", "establishDate");
        StringBuilder builder = new StringBuilder();
        appendSummary(builder, "企业名称", fields.getString("companyName"));
        appendSummary(builder, "统一社会信用代码", fields.getString("creditCode"));
        appendSummary(builder, "法定代表人", fields.getString("legalRepresentative"));
        appendSummary(builder, "行业", fields.getString("industry"));
        appendSummary(builder, "注册资本", fields.getString("registeredCapital"));
        appendSummary(builder, "状态", status);
        appendSummary(builder, "成立时间", resolveText(fields.getString("establishDate"), startDate));
        appendSummary(builder, "经营范围", scope);
        appendSummary(builder, "简介", fields.getString("description"));
        return shrink(builder.toString(), 500);
    }

    private String buildTextSummary(JSONObject fields, String text) {
        StringBuilder builder = new StringBuilder();
        appendSummary(builder, "企业名称", fields.getString("companyName"));
        appendSummary(builder, "统一社会信用代码", fields.getString("creditCode"));
        appendSummary(builder, "法定代表人", fields.getString("legalRepresentative"));
        appendSummary(builder, "电话", fields.getString("phone"));
        appendSummary(builder, "邮箱", fields.getString("email"));
        appendSummary(builder, "官网", fields.getString("website"));
        appendSummary(builder, "地址", fields.getString("address"));
        appendSummary(builder, "注册资本", fields.getString("registeredCapital"));
        appendSummary(builder, "注册时间", fields.getString("establishDate"));
        appendSummary(builder, "简介", fields.getString("description"));
        if (builder.length() > 0) {
            return shrink(builder.toString(), 500);
        }
        return shrink(text, 500);
    }

    private String extractMetaText(String html) {
        if (blank(html)) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        appendSummary(builder, "", firstMetaContent(html, "description"));
        appendSummary(builder, "", firstMetaContent(html, "keywords"));
        appendSummary(builder, "", firstTitle(html));
        return builder.toString();
    }

    private String firstMetaContent(String html, String name) {
        String value = matchMetaContent(html, Pattern.compile("(?is)<meta[^>]+(?:name|property)=[\"']"
                + Pattern.quote(name)
                + "[\"'][^>]+content=[\"']([^\"']{1,1000})[\"'][^>]*>"));
        if (!blank(value)) {
            return value;
        }
        return matchMetaContent(html, Pattern.compile("(?is)<meta[^>]+content=[\"']([^\"']{1,1000})[\"'][^>]+"
                + "(?:name|property)=[\"']"
                + Pattern.quote(name)
                + "[\"'][^>]*>"));
    }

    private String matchMetaContent(String html, Pattern pattern) {
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return decodeHtml(matcher.group(1));
        }
        return "";
    }

    private String firstTitle(String html) {
        Pattern pattern = Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            return decodeHtml(matcher.group(1));
        }
        return "";
    }

    private void mergeProfileFields(JSONObject target, JSONObject source) {
        if (target == null || source == null) {
            return;
        }
        mergeText(target, source, "companyName");
        mergeText(target, source, "creditCode");
        mergeText(target, source, "legalRepresentative");
        mergeText(target, source, "keyPerson");
        mergeText(target, source, "companyScale");
        mergeText(target, source, "industry");
        mergeText(target, source, "phone");
        mergeText(target, source, "email");
        mergeText(target, source, "website");
        mergeText(target, source, "address");
        mergeText(target, source, "registeredCapital");
        mergeText(target, source, "establishDate");
        mergeText(target, source, "description");
        mergeText(target, source, "sourceSummary");
    }

    private boolean hasProfileFields(JSONObject fields) {
        if (fields == null) {
            return false;
        }
        return !blank(fields.getString("legalRepresentative"))
                || !blank(fields.getString("creditCode"))
                || !blank(fields.getString("keyPerson"))
                || !blank(fields.getString("companyScale"))
                || !blank(fields.getString("industry"))
                || !blank(fields.getString("phone"))
                || !blank(fields.getString("email"))
                || !blank(fields.getString("website"))
                || !blank(fields.getString("address"))
                || !blank(fields.getString("registeredCapital"))
                || !blank(fields.getString("establishDate"))
                || !blank(fields.getString("description"));
    }

    private String resolveDetailMessage(String html, String text, JSONObject fields) {
        if (hasProfileFields(fields)) {
            return "详情抓取完成";
        }
        String plainText = normalizePlainText(decodeHtml(html));
        if (plainText.contains("accessrestriction") || plainText.contains("访问受限")
                || plainText.contains("安全验证") || plainText.contains("异常访问")) {
            return "详情页访问受限，未提取到客户档案字段";
        }
        if (blank(text)) {
            return "未提取到详情正文";
        }
        return "详情正文已抓取，但未识别到客户档案字段";
    }

    private void fillField(JSONObject target, String key, String value) {
        if (target == null || blank(key) || blank(value)) {
            return;
        }
        if (blank(target.getString(key))) {
            target.put(key, value.trim());
        }
    }

    private void appendSummary(StringBuilder builder, String label, String value) {
        if (blank(value)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("；");
        }
        if (!blank(label)) {
            builder.append(label).append("：");
        }
        builder.append(value.trim());
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
                + "\\s*[:：]?\\s*(.{1,220}?)(?=\\s*(统一社会信用代码|社会信用代码|信用代码|电话|更多电话|邮箱|网址|地址|注册地址|企业地址|通信地址|法定代表人|法人代表|法人|负责人|疑似实控人|注册资本|注册资金|注册时间|成立日期|成立时间|注册日期|简介|公司简介|企业简介|经营范围|所属行业|行业|人员规模|企业规模|公司规模|参保人数)\\s*[:：]?|$)");
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private String firstPhone(String text) {
        if (blank(text)) {
            return "";
        }
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
        if (blank(text)) {
            return "";
        }
        String value = firstMatch(text, "邮箱", "电子邮箱", "Email");
        Matcher matcher = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}").matcher(
                blank(value) ? text : value);
        return matcher.find() ? matcher.group() : "";
    }

    private String firstWebsite(String text) {
        if (blank(text)) {
            return "";
        }
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
        text = text.replaceAll("【[^】]{1,20}】", "").trim();
        text = text.replaceAll("\\s*(履历|TA有\\d+家企业|更多\\d*|历史变动|附近公司|查看|复制|展开).*$", "").trim();
        text = text.replaceAll("(查看|复制|展开|更多)$", "").trim();
        text = decodeUnicodeEscapes(text);
        text = decodeHtml(text);
        return text;
    }

    private JSONObject emptyProfileDraft(String companyName, String searchedAt) {
        JSONObject profile = new JSONObject();
        profile.put("available", false);
        profile.put("companyName", trimToEmpty(companyName));
        profile.put("creditCode", "");
        profile.put("legalRepresentative", "");
        profile.put("keyPerson", "");
        profile.put("companyScale", "");
        profile.put("industry", "");
        profile.put("phone", "");
        profile.put("email", "");
        profile.put("website", "");
        profile.put("address", "");
        profile.put("registeredCapital", "");
        profile.put("establishDate", "");
        profile.put("description", "");
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
        mergeText(target, fields, "creditCode");
        mergeText(target, fields, "legalRepresentative");
        mergeText(target, fields, "keyPerson");
        mergeText(target, fields, "companyScale");
        mergeText(target, fields, "industry");
        mergeText(target, fields, "phone");
        mergeText(target, fields, "email");
        mergeText(target, fields, "website");
        mergeText(target, fields, "address");
        mergeText(target, fields, "registeredCapital");
        mergeText(target, fields, "establishDate");
        mergeText(target, fields, "description");
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

    private String decodeUnicodeEscapes(String value) {
        if (blank(value) || !value.contains("\\u")) {
            return trimToEmpty(value);
        }
        Matcher matcher = Pattern.compile("\\\\u([0-9a-fA-F]{4})").matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            char ch = (char) Integer.parseInt(matcher.group(1), 16);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(String.valueOf(ch)));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
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
        builder.append(" 爱企查 公司 负责人 公司规模 行业 电话 官网 地址 工商");
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
