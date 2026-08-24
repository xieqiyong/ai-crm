package com.hz.crm.web.channel;

import com.hz.crm.application.channel.dto.ChannelSourceImportRow;
import com.hz.crm.common.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ChannelSourceExcelImportParser {

    private static final long MAX_FILE_SIZE = 20L * 1024L * 1024L;

    private static final int MAX_ROW_COUNT = 10000;

    private static final int HEADER_SCAN_COUNT = 20;

    private static final List<String> NAME_HEADERS = Arrays.asList(
            "您的姓名", "姓名", "名称", "联系人", "联系人姓名", "客户姓名");

    private static final List<String> COMPANY_HEADERS = Arrays.asList(
            "所在单位名称", "单位名称", "公司名称", "企业名称", "公司", "所在公司");

    private static final List<String> PHONE_HEADERS = Arrays.asList(
            "您的手机号-手机号", "您的手机号－手机号", "手机号-手机号", "手机号（手机号）",
            "您的手机号", "手机号", "手机号码", "联系电话", "电话", "手机");

    private static final List<String> EMAIL_HEADERS = Arrays.asList(
            "您的企业邮箱-电子邮箱", "企业邮箱", "电子邮箱", "联系邮箱", "邮箱");

    private static final List<String> SUBMITTED_AT_HEADERS = Arrays.asList(
            "填写时间", "提交时间", "创建时间", "收集时间", "答题时间", "填写日期", "提交日期");

    private static final List<DateTimeFormatter> DATE_TIME_FORMATTERS = Arrays.asList(
            DateTimeFormatter.ofPattern("yyyy-M-d H:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy/M/d H:mm:ss.SSS"),
            DateTimeFormatter.ofPattern("yyyy-M-d H:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/M/d H:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy.M.d H:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy年M月d日 H:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-M-d H:mm"),
            DateTimeFormatter.ofPattern("yyyy/M/d H:mm"),
            DateTimeFormatter.ofPattern("yyyy.M.d H:mm"),
            DateTimeFormatter.ofPattern("yyyy年M月d日 H:mm"),
            DateTimeFormatter.ofPattern("yyyy-M-d a h:mm:ss", Locale.CHINA),
            DateTimeFormatter.ofPattern("yyyy/M/d a h:mm:ss", Locale.CHINA),
            DateTimeFormatter.ofPattern("yyyy-M-d a h:mm", Locale.CHINA),
            DateTimeFormatter.ofPattern("yyyy/M/d a h:mm", Locale.CHINA));

    private static final List<DateTimeFormatter> DATE_FORMATTERS = Arrays.asList(
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy.M.d"),
            DateTimeFormatter.ofPattern("yyyy年M月d日"));

    public List<ChannelSourceImportRow> parse(MultipartFile file) {
        validateFile(file);
        try (InputStream inputStream = file.getInputStream();
                Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = resolveSheet(workbook);
            return parseSheet(workbook, sheet);
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException("CHANNEL_SOURCE_IMPORT_003", "Excel文件读取失败");
        } catch (RuntimeException ex) {
            throw new BusinessException("CHANNEL_SOURCE_IMPORT_004", "Excel文件格式不正确或内容已损坏");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("CHANNEL_SOURCE_IMPORT_005", "请选择企微智能表格导出的Excel文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("CHANNEL_SOURCE_IMPORT_006", "Excel文件不能超过20MB");
        }
        String fileName = file.getOriginalFilename();
        String lowerName = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".xlsx") && !lowerName.endsWith(".xls")) {
            throw new BusinessException("CHANNEL_SOURCE_IMPORT_007", "仅支持xlsx和xls格式的Excel文件");
        }
    }

    private Sheet resolveSheet(Workbook workbook) {
        for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
            Sheet sheet = workbook.getSheetAt(index);
            if (sheet != null && sheet.getPhysicalNumberOfRows() > 0) {
                return sheet;
            }
        }
        throw new BusinessException("CHANNEL_SOURCE_IMPORT_008", "Excel文件中没有可导入的工作表");
    }

    private List<ChannelSourceImportRow> parseSheet(Workbook workbook, Sheet sheet) {
        DataFormatter formatter = new DataFormatter(Locale.CHINA);
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        Row headerRow = resolveHeaderRow(sheet, formatter, evaluator);
        Map<Integer, String> headers = readHeaders(headerRow, formatter, evaluator);
        validateHeaders(headers);
        int dataRowCount = sheet.getLastRowNum() - headerRow.getRowNum();
        if (dataRowCount > MAX_ROW_COUNT) {
            throw new BusinessException("CHANNEL_SOURCE_IMPORT_009", "单次最多导入10000行渠道数据");
        }
        List<ChannelSourceImportRow> rows = new ArrayList<ChannelSourceImportRow>();
        for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row dataRow = sheet.getRow(rowIndex);
            Map<String, String> values = readRow(dataRow, headers, formatter, evaluator);
            if (!values.isEmpty()) {
                rows.add(toImportRow(
                        rowIndex + 1,
                        values,
                        readSubmittedAt(dataRow, headers, formatter, evaluator)));
            }
        }
        if (rows.isEmpty()) {
            throw new BusinessException("CHANNEL_SOURCE_IMPORT_010", "Excel文件中没有可导入的数据");
        }
        return rows;
    }

    private Row resolveHeaderRow(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        Row bestRow = null;
        int bestScore = -1;
        int endRow = Math.min(sheet.getLastRowNum(), sheet.getFirstRowNum() + HEADER_SCAN_COUNT - 1);
        for (int index = sheet.getFirstRowNum(); index <= endRow; index++) {
            Row row = sheet.getRow(index);
            if (row == null) {
                continue;
            }
            Map<Integer, String> headers = readHeaders(row, formatter, evaluator);
            int score = headers.size() + coreHeaderCount(headers) * 100;
            if (score > bestScore) {
                bestScore = score;
                bestRow = row;
            }
        }
        if (bestRow == null) {
            throw new BusinessException("CHANNEL_SOURCE_IMPORT_011", "Excel表头不能为空");
        }
        return bestRow;
    }

    private int coreHeaderCount(Map<Integer, String> headers) {
        int count = 0;
        List<String> values = new ArrayList<String>(headers.values());
        if (containsAny(values, NAME_HEADERS)) {
            count++;
        }
        if (containsAny(values, COMPANY_HEADERS)) {
            count++;
        }
        if (containsAny(values, PHONE_HEADERS)) {
            count++;
        }
        if (containsAny(values, EMAIL_HEADERS)) {
            count++;
        }
        return count;
    }

    private Map<Integer, String> readHeaders(
            Row row,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {
        Map<Integer, String> headers = new LinkedHashMap<Integer, String>();
        if (row == null || row.getFirstCellNum() < 0) {
            return headers;
        }
        for (int index = row.getFirstCellNum(); index < row.getLastCellNum(); index++) {
            String header = cellText(row.getCell(index), formatter, evaluator);
            if (StringUtils.hasText(header)) {
                headers.put(Integer.valueOf(index), normalizeHeader(header));
            }
        }
        return headers;
    }

    private String normalizeHeader(String header) {
        if (!StringUtils.hasText(header)) {
            return "";
        }
        return header.trim()
                .replace("*", "")
                .replace("＊", "")
                .replaceAll("\\s+", "");
    }

    private void validateHeaders(Map<Integer, String> headers) {
        List<String> values = new ArrayList<String>(headers.values());
        boolean valid = containsAny(values, NAME_HEADERS)
                || containsAny(values, COMPANY_HEADERS)
                || containsAny(values, PHONE_HEADERS)
                || containsAny(values, EMAIL_HEADERS);
        if (!valid) {
            throw new BusinessException(
                    "CHANNEL_SOURCE_IMPORT_012", "Excel必须包含联系人、公司名称、联系电话或邮箱中的至少一列");
        }
    }

    private Map<String, String> readRow(
            Row row,
            Map<Integer, String> headers,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        if (row == null) {
            return values;
        }
        for (Map.Entry<Integer, String> entry : headers.entrySet()) {
            String value = cellText(row.getCell(entry.getKey().intValue()), formatter, evaluator);
            if (StringUtils.hasText(value)) {
                values.put(entry.getValue(), value.trim());
            }
        }
        return values;
    }

    private ChannelSourceImportRow toImportRow(
            int rowNumber, Map<String, String> values, LocalDateTime submittedAt) {
        ChannelSourceImportRow row = new ChannelSourceImportRow();
        row.setRowNumber(rowNumber);
        row.setContactName(findValue(values, NAME_HEADERS));
        row.setCompanyName(findValue(values, COMPANY_HEADERS));
        row.setPhone(findValue(values, PHONE_HEADERS));
        row.setEmail(findValue(values, EMAIL_HEADERS));
        row.setSubmittedAt(submittedAt);
        row.setValues(new LinkedHashMap<String, String>(values));
        return row;
    }

    private LocalDateTime readSubmittedAt(
            Row row,
            Map<Integer, String> headers,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {
        if (row == null) {
            return null;
        }
        for (Map.Entry<Integer, String> entry : headers.entrySet()) {
            if (!isSubmittedAtHeader(entry.getValue())) {
                continue;
            }
            Cell cell = row.getCell(entry.getKey().intValue());
            if (cell != null
                    && cell.getCellType() == CellType.NUMERIC
                    && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue();
            }
            return parseDateTime(cellText(cell, formatter, evaluator));
        }
        return null;
    }

    private boolean isSubmittedAtHeader(String header) {
        if (!StringUtils.hasText(header)) {
            return false;
        }
        return SUBMITTED_AT_HEADERS.contains(header)
                || header.contains("填写时间")
                || header.contains("提交时间")
                || header.contains("收集时间")
                || header.contains("答题时间");
    }

    private LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (DateTimeParseException ignored) {
        }
        String text = value.trim().replace('T', ' ').replaceAll("\\s+", " ");
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
                continue;
            }
        }
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(text, formatter).atStartOfDay();
            } catch (DateTimeParseException ignored) {
                continue;
            }
        }
        return null;
    }

    private String cellText(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        if (cell == null) {
            return "";
        }
        return formatter.formatCellValue(cell, evaluator);
    }

    private String findValue(Map<String, String> values, List<String> aliases) {
        for (String alias : aliases) {
            String value = values.get(alias);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private boolean containsAny(List<String> values, List<String> aliases) {
        for (String alias : aliases) {
            if (values.contains(alias)) {
                return true;
            }
        }
        return false;
    }
}
