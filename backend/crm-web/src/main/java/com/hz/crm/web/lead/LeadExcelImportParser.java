package com.hz.crm.web.lead;

import com.hz.crm.application.lead.dto.LeadImportRow;
import com.hz.crm.common.exception.BusinessException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Component
public class LeadExcelImportParser {

    private static final long MAX_FILE_SIZE = 10L * 1024L * 1024L;

    private static final int MAX_ROW_COUNT = 5000;

    private static final List<String> NAME_HEADERS = Arrays.asList(
            "您的姓名", "姓名", "名称", "联系人", "线索名称");

    private static final List<String> COMPANY_HEADERS = Arrays.asList(
            "所在单位名称", "单位名称", "公司名称", "企业名称", "公司");

    private static final List<String> PHONE_HEADERS = Arrays.asList(
            "您的手机号-手机号", "手机号", "联系电话", "电话", "手机");

    private static final List<String> EMAIL_HEADERS = Arrays.asList(
            "您的企业邮箱-电子邮箱", "企业邮箱", "电子邮箱", "联系邮箱", "邮箱");

    private static final List<String> SOURCE_HEADERS = Arrays.asList(
            "用户类型", "线索来源", "来源", "渠道来源");

    public List<LeadImportRow> parse(MultipartFile file) {
        validateFile(file);
        try (InputStream inputStream = file.getInputStream();
                Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = resolveSheet(workbook);
            return parseSheet(workbook, sheet);
        } catch (BusinessException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new BusinessException("LEAD_IMPORT_002", "Excel文件读取失败");
        } catch (RuntimeException ex) {
            throw new BusinessException("LEAD_IMPORT_003", "Excel文件格式不正确或内容已损坏");
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("LEAD_IMPORT_001", "请选择要导入的Excel文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException("LEAD_IMPORT_004", "Excel文件不能超过10MB");
        }
        String fileName = file.getOriginalFilename();
        String lowerName = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".xlsx") && !lowerName.endsWith(".xls")) {
            throw new BusinessException("LEAD_IMPORT_005", "仅支持xlsx和xls格式的Excel文件");
        }
    }

    private Sheet resolveSheet(Workbook workbook) {
        for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
            Sheet sheet = workbook.getSheetAt(index);
            if (sheet != null && sheet.getPhysicalNumberOfRows() > 0) {
                return sheet;
            }
        }
        throw new BusinessException("LEAD_IMPORT_006", "Excel文件中没有可导入的工作表");
    }

    private List<LeadImportRow> parseSheet(Workbook workbook, Sheet sheet) {
        Row headerRow = sheet.getRow(sheet.getFirstRowNum());
        if (headerRow == null) {
            throw new BusinessException("LEAD_IMPORT_007", "Excel表头不能为空");
        }
        DataFormatter formatter = new DataFormatter(Locale.CHINA);
        FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
        Map<Integer, String> headers = readHeaders(headerRow, formatter, evaluator);
        validateHeaders(headers);
        List<LeadImportRow> rows = new ArrayList<LeadImportRow>();
        int lastRowNumber = sheet.getLastRowNum();
        if (lastRowNumber - headerRow.getRowNum() > MAX_ROW_COUNT) {
            throw new BusinessException("LEAD_IMPORT_008", "单次最多导入5000行线索");
        }
        for (int rowIndex = headerRow.getRowNum() + 1; rowIndex <= lastRowNumber; rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            Map<String, String> values = readRow(row, headers, formatter, evaluator);
            if (values.isEmpty()) {
                continue;
            }
            rows.add(toImportRow(rowIndex + 1, values));
        }
        if (rows.isEmpty()) {
            throw new BusinessException("LEAD_IMPORT_009", "Excel文件中没有可导入的数据");
        }
        return rows;
    }

    private Map<Integer, String> readHeaders(
            Row row,
            DataFormatter formatter,
            FormulaEvaluator evaluator) {
        Map<Integer, String> headers = new LinkedHashMap<Integer, String>();
        for (int index = row.getFirstCellNum(); index < row.getLastCellNum(); index++) {
            String header = cellText(row.getCell(index), formatter, evaluator);
            if (StringUtils.hasText(header)) {
                headers.put(Integer.valueOf(index), header.trim());
            }
        }
        return headers;
    }

    private void validateHeaders(Map<Integer, String> headers) {
        List<String> values = new ArrayList<String>(headers.values());
        if (!containsAny(values, NAME_HEADERS) && !containsAny(values, COMPANY_HEADERS)) {
            throw new BusinessException(
                    "LEAD_IMPORT_010",
                    "Excel必须包含姓名、名称、联系人、公司名称或所在单位名称中的至少一列");
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

    private LeadImportRow toImportRow(int rowNumber, Map<String, String> values) {
        LeadImportRow row = new LeadImportRow();
        row.setRowNumber(rowNumber);
        row.setName(findValue(values, NAME_HEADERS));
        row.setCompanyName(findValue(values, COMPANY_HEADERS));
        row.setPhone(findValue(values, PHONE_HEADERS));
        row.setEmail(findValue(values, EMAIL_HEADERS));
        row.setSource(findValue(values, SOURCE_HEADERS));
        Map<String, String> additionalFields = new LinkedHashMap<String, String>(values);
        removeAliases(additionalFields, NAME_HEADERS);
        removeAliases(additionalFields, COMPANY_HEADERS);
        removeAliases(additionalFields, PHONE_HEADERS);
        removeAliases(additionalFields, EMAIL_HEADERS);
        row.setAdditionalFields(additionalFields);
        return row;
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

    private void removeAliases(Map<String, String> values, List<String> aliases) {
        for (String alias : aliases) {
            values.remove(alias);
        }
    }
}
