package com.hz.crm.application.mail;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hz.crm.application.mail.dto.MailAccountResponse;
import com.hz.crm.application.mail.dto.MailAccountSaveRequest;
import com.hz.crm.application.mail.dto.MailAttachment;
import com.hz.crm.application.mail.dto.MailLogQuery;
import com.hz.crm.application.mail.dto.MailSendRequest;
import com.hz.crm.application.mail.dto.MailCustomerOptionResponse;
import com.hz.crm.common.api.PageData;
import com.hz.crm.common.exception.BusinessException;
import com.hz.crm.common.id.SnowflakeIdGenerator;
import com.hz.crm.common.time.DateTimes;
import com.hz.crm.common.user.UserDataScopeValidator;
import com.hz.crm.domain.customer.CustomerEntity;
import com.hz.crm.domain.customer.mapper.CustomerMapper;
import com.hz.crm.domain.mail.MailAccountEntity;
import com.hz.crm.domain.mail.MailSendLogEntity;
import com.hz.crm.domain.mail.mapper.MailAccountMapper;
import com.hz.crm.domain.mail.mapper.MailSendLogMapper;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MailApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MailApplicationService.class);

    private static final int MAX_ATTACHMENT_COUNT = 5;

    private static final long MAX_TOTAL_ATTACHMENT_BYTES = 10L * 1024L * 1024L;

    @Autowired
    private MailAccountMapper mailAccountMapper;

    @Autowired
    private MailSendLogMapper mailSendLogMapper;

    @Autowired
    private CustomerMapper customerMapper;

    @Autowired
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Autowired
    private UserDataScopeValidator userDataScopeValidator;

    public MailAccountResponse account(Long tenantId) {
        return toAccountResponse(findAccount(tenantId));
    }

    @Transactional
    public MailAccountResponse saveAccount(Long tenantId, MailAccountSaveRequest request) {
        validateAccount(request);
        MailAccountEntity entity = findAccount(tenantId);
        boolean create = entity == null;
        if (entity == null) {
            entity = new MailAccountEntity();
            entity.setId(snowflakeIdGenerator.nextId());
            entity.setTenantId(tenantId);
            entity.setCreatedAt(DateTimes.now());
        }
        entity.setHost(request.getHost().trim());
        entity.setPort(request.getPort());
        entity.setUsername(request.getUsername().trim());
        if (trimToNull(request.getPassword()) != null) {
            entity.setPassword(request.getPassword());
        } else if (trimToNull(entity.getPassword()) == null) {
            throw new BusinessException("MAIL_ACCOUNT_005", "首次配置时必须填写SMTP密码或授权码");
        }
        entity.setFromAddress(validateEmail(request.getFromAddress(), "发件邮箱格式不正确"));
        entity.setFromName(trimToNull(request.getFromName()));
        entity.setSslEnabled(Boolean.TRUE.equals(request.getSslEnabled()));
        entity.setStarttlsEnabled(request.getStarttlsEnabled() == null
                || Boolean.TRUE.equals(request.getStarttlsEnabled()));
        entity.setEnabled(request.getEnabled() == null || Boolean.TRUE.equals(request.getEnabled()));
        entity.setUpdatedAt(DateTimes.now());
        if (create) {
            mailAccountMapper.insert(entity);
        } else {
            mailAccountMapper.updateById(entity);
        }
        return toAccountResponse(entity);
    }

    public PageData<MailSendLogEntity> pageLogs(
            Long tenantId, Long userId, String dataScope, MailLogQuery query) {
        MailLogQuery safeQuery = query == null ? new MailLogQuery() : query;
        List<Long> accessibleUserIds = null;
        if (!"ALL".equals(dataScope)) {
            accessibleUserIds =
                    userDataScopeValidator.listAccessibleUserIds(tenantId, userId, dataScope);
        }
        if (accessibleUserIds != null && accessibleUserIds.isEmpty()) {
            return PageData.of(
                    0L,
                    safeQuery.safePageNo(),
                    safeQuery.safePageSize(),
                    new ArrayList<MailSendLogEntity>());
        }
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MailSendLogEntity> countWrapper =
                Wrappers.<MailSendLogEntity>lambdaQuery()
                        .eq(MailSendLogEntity::getTenantId, tenantId)
                        .eq(MailSendLogEntity::isDeleted, false);
        if (accessibleUserIds != null) {
            countWrapper.in(MailSendLogEntity::getSenderId, accessibleUserIds);
        }
        Long total = mailSendLogMapper.selectCount(countWrapper);
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MailSendLogEntity> wrapper =
                Wrappers.<MailSendLogEntity>lambdaQuery()
                        .eq(MailSendLogEntity::getTenantId, tenantId)
                        .eq(MailSendLogEntity::isDeleted, false)
                        .orderByDesc(MailSendLogEntity::getCreatedAt);
        if (accessibleUserIds != null) {
            wrapper.in(MailSendLogEntity::getSenderId, accessibleUserIds);
        }
        int offset = (safeQuery.safePageNo() - 1) * safeQuery.safePageSize();
        wrapper.last("limit " + safeQuery.safePageSize() + " offset " + offset);
        List<MailSendLogEntity> records = mailSendLogMapper.selectList(wrapper);
        return PageData.of(
                total == null ? 0L : total.longValue(),
                safeQuery.safePageNo(),
                safeQuery.safePageSize(),
                records);
    }

    @Transactional
    public void deleteLog(Long tenantId, Long userId, String dataScope, Long id) {
        if (id == null) {
            throw new BusinessException("MAIL_LOG_001", "邮件发送记录编号不能为空");
        }
        MailSendLogEntity log = mailSendLogMapper.selectOne(
                Wrappers.<MailSendLogEntity>lambdaQuery()
                        .eq(MailSendLogEntity::getId, id)
                        .eq(MailSendLogEntity::getTenantId, tenantId)
                        .eq(MailSendLogEntity::isDeleted, false));
        if (log == null) {
            throw new BusinessException("MAIL_LOG_002", "邮件发送记录不存在");
        }
        userDataScopeValidator.checkOwnerAccess(
                tenantId, userId, dataScope, log.getSenderId());
        log.setDeleted(true);
        log.setUpdatedAt(DateTimes.now());
        mailSendLogMapper.updateById(log);
    }

    public List<MailCustomerOptionResponse> customerOptions(
            Long tenantId, Long userId, String dataScope) {
        List<Long> accessibleUserIds = null;
        if (!"ALL".equals(dataScope)) {
            accessibleUserIds =
                    userDataScopeValidator.listAccessibleUserIds(tenantId, userId, dataScope);
        }
        if (accessibleUserIds != null && accessibleUserIds.isEmpty()) {
            return new ArrayList<MailCustomerOptionResponse>();
        }
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CustomerEntity> wrapper =
                Wrappers.<CustomerEntity>lambdaQuery()
                        .eq(CustomerEntity::getTenantId, tenantId)
                        .eq(CustomerEntity::isDeleted, false)
                        .isNotNull(CustomerEntity::getContactEmail)
                        .ne(CustomerEntity::getContactEmail, "")
                        .orderByAsc(CustomerEntity::getName)
                        .last("limit 200");
        if (accessibleUserIds != null) {
            wrapper.in(CustomerEntity::getOwnerId, accessibleUserIds);
        }
        List<CustomerEntity> customers = customerMapper.selectList(wrapper);
        List<MailCustomerOptionResponse> responses = new ArrayList<MailCustomerOptionResponse>();
        for (CustomerEntity customer : customers) {
            MailCustomerOptionResponse response = new MailCustomerOptionResponse();
            response.setId(customer.getId());
            response.setName(customer.getName());
            response.setContactName(customer.getContactName());
            response.setContactEmail(customer.getContactEmail());
            responses.add(response);
        }
        return responses;
    }

    public void send(
            Long tenantId,
            Long userId,
            String dataScope,
            MailSendRequest request,
            List<MailAttachment> attachments) {
        MailAccountEntity account = requireEnabledAccount(tenantId);
        CustomerEntity customer = findCustomer(tenantId, request);
        if (customer != null) {
            userDataScopeValidator.checkOwnerAccess(
                    tenantId, userId, dataScope, customer.getOwnerId());
        }
        String requestEmail = request == null ? null : trimToNull(request.getRecipientEmail());
        String customerEmail = customer == null ? null : customer.getContactEmail();
        String recipient = validateEmail(
                requestEmail == null ? customerEmail : requestEmail,
                "请填写有效的收件邮箱");
        String subject = validateSubject(request.getSubject());
        String bodyHtml = trimToNull(request.getBodyHtml());
        if (bodyHtml == null) {
            throw new BusinessException("MAIL_SEND_004", "邮件正文不能为空");
        }
        List<MailAttachment> safeAttachments = validateAttachments(attachments);
        MailSendLogEntity log = createLog(
                tenantId, userId, customer, recipient, subject, bodyHtml, safeAttachments);
        mailSendLogMapper.insert(log);
        try {
            JavaMailSenderImpl sender = buildSender(account);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, !safeAttachments.isEmpty(), StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(
                    account.getFromAddress(),
                    trimToNull(account.getFromName()) == null ? account.getFromAddress() : account.getFromName(),
                    StandardCharsets.UTF_8.name()));
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(bodyHtml, true);
            for (MailAttachment attachment : safeAttachments) {
                helper.addAttachment(attachment.getName(), new ByteArrayResource(attachment.getContent()));
            }
            sender.send(message);
            log.setStatus("SENT");
            log.setSentAt(DateTimes.now());
            log.setUpdatedAt(DateTimes.now());
            mailSendLogMapper.updateById(log);
        } catch (Exception ex) {
            String failureMessage = resolveFailureMessage(ex);
            log.setStatus("FAILED");
            log.setErrorMessage(failureMessage);
            log.setUpdatedAt(DateTimes.now());
            mailSendLogMapper.updateById(log);
            LOGGER.error(
                    "邮件发送失败，租户编号：{}，发送人编号：{}，SMTP服务器：{}，端口：{}，SMTP账号：{}，收件邮箱：{}",
                    tenantId,
                    userId,
                    account.getHost(),
                    account.getPort(),
                    account.getUsername(),
                    recipient,
                    ex);
            throw new BusinessException("MAIL_SEND_005", "邮件发送失败：" + failureMessage);
        }
    }

    private MailSendLogEntity createLog(
            Long tenantId,
            Long userId,
            CustomerEntity customer,
            String recipient,
            String subject,
            String bodyHtml,
            List<MailAttachment> attachments) {
        MailSendLogEntity log = new MailSendLogEntity();
        log.setId(snowflakeIdGenerator.nextId());
        log.setTenantId(tenantId);
        if (customer != null) {
            log.setCustomerId(customer.getId());
            log.setCustomerName(customer.getName());
        }
        log.setRecipientEmail(recipient);
        log.setSubject(subject);
        log.setBodyHtml(bodyHtml);
        log.setAttachmentNames(joinAttachmentNames(attachments));
        log.setStatus("SENDING");
        log.setSenderId(userId);
        log.setCreatedAt(DateTimes.now());
        log.setUpdatedAt(DateTimes.now());
        return log;
    }

    private JavaMailSenderImpl buildSender(MailAccountEntity account) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(account.getHost());
        sender.setPort(account.getPort());
        sender.setUsername(account.getUsername());
        sender.setPassword(account.getPassword());
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.connectiontimeout", "10000");
        properties.put("mail.smtp.timeout", "20000");
        properties.put("mail.smtp.writetimeout", "20000");
        properties.put("mail.smtp.ssl.enable", String.valueOf(account.isSslEnabled()));
        properties.put("mail.smtp.starttls.enable", String.valueOf(account.isStarttlsEnabled()));
        return sender;
    }

    private CustomerEntity findCustomer(Long tenantId, MailSendRequest request) {
        if (request == null) {
            throw new BusinessException("MAIL_SEND_001", "邮件发送请求不能为空");
        }
        if (request.getCustomerId() == null) {
            return null;
        }
        CustomerEntity customer = customerMapper.selectOne(Wrappers.<CustomerEntity>lambdaQuery()
                .eq(CustomerEntity::getId, request.getCustomerId())
                .eq(CustomerEntity::getTenantId, tenantId)
                .eq(CustomerEntity::isDeleted, false));
        if (customer == null) {
            throw new BusinessException("MAIL_SEND_002", "客户不存在");
        }
        return customer;
    }

    private MailAccountEntity requireEnabledAccount(Long tenantId) {
        MailAccountEntity account = findAccount(tenantId);
        if (account == null || !account.isEnabled()) {
            throw new BusinessException("MAIL_ACCOUNT_001", "请先配置并启用SMTP发件账号");
        }
        return account;
    }

    private MailAccountEntity findAccount(Long tenantId) {
        return mailAccountMapper.selectOne(Wrappers.<MailAccountEntity>lambdaQuery()
                .eq(MailAccountEntity::getTenantId, tenantId)
                .eq(MailAccountEntity::isDeleted, false)
                .last("limit 1"));
    }

    private MailAccountResponse toAccountResponse(MailAccountEntity entity) {
        MailAccountResponse response = new MailAccountResponse();
        if (entity == null) {
            response.setConfigured(false);
            response.setPort(465);
            response.setSslEnabled(true);
            response.setStarttlsEnabled(false);
            response.setEnabled(true);
            return response;
        }
        response.setConfigured(true);
        response.setHost(entity.getHost());
        response.setPort(entity.getPort());
        response.setUsername(entity.getUsername());
        response.setPasswordConfigured(trimToNull(entity.getPassword()) != null);
        response.setFromAddress(entity.getFromAddress());
        response.setFromName(entity.getFromName());
        response.setSslEnabled(entity.isSslEnabled());
        response.setStarttlsEnabled(entity.isStarttlsEnabled());
        response.setEnabled(entity.isEnabled());
        return response;
    }

    private void validateAccount(MailAccountSaveRequest request) {
        if (request == null || trimToNull(request.getHost()) == null) {
            throw new BusinessException("MAIL_ACCOUNT_002", "SMTP服务器不能为空");
        }
        if (request.getPort() == null || request.getPort() < 1 || request.getPort() > 65535) {
            throw new BusinessException("MAIL_ACCOUNT_003", "SMTP端口不正确");
        }
        if (trimToNull(request.getUsername()) == null) {
            throw new BusinessException("MAIL_ACCOUNT_004", "SMTP账号不能为空");
        }
        rejectHeaderLine(request.getFromName(), "发件人名称");
    }

    private List<MailAttachment> validateAttachments(List<MailAttachment> attachments) {
        List<MailAttachment> values =
                attachments == null ? new ArrayList<MailAttachment>() : attachments;
        if (values.size() > MAX_ATTACHMENT_COUNT) {
            throw new BusinessException("MAIL_ATTACHMENT_001", "单封邮件最多上传5个附件");
        }
        long totalBytes = 0L;
        for (MailAttachment attachment : values) {
            if (attachment == null || attachment.getContent() == null || attachment.getContent().length == 0) {
                throw new BusinessException("MAIL_ATTACHMENT_002", "附件内容不能为空");
            }
            if (trimToNull(attachment.getName()) == null) {
                throw new BusinessException("MAIL_ATTACHMENT_003", "附件名称不能为空");
            }
            totalBytes += attachment.getContent().length;
        }
        if (totalBytes > MAX_TOTAL_ATTACHMENT_BYTES) {
            throw new BusinessException("MAIL_ATTACHMENT_004", "附件总大小不能超过10MB");
        }
        return values;
    }

    private String validateSubject(String subject) {
        String value = trimToNull(subject);
        if (value == null) {
            throw new BusinessException("MAIL_SEND_003", "邮件主题不能为空");
        }
        if (value.length() > 256) {
            throw new BusinessException("MAIL_SEND_003", "邮件主题不能超过256个字符");
        }
        rejectHeaderLine(value, "邮件主题");
        return value;
    }

    private String validateEmail(String email, String message) {
        String value = trimToNull(email);
        if (value == null) {
            throw new BusinessException("MAIL_EMAIL_001", message);
        }
        try {
            InternetAddress address = new InternetAddress(value);
            address.validate();
            rejectHeaderLine(value, "邮箱");
            return value;
        } catch (Exception ex) {
            throw new BusinessException("MAIL_EMAIL_001", message);
        }
    }

    private void rejectHeaderLine(String value, String fieldName) {
        if (value != null && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0)) {
            throw new BusinessException("MAIL_HEADER_001", fieldName + "不能包含换行符");
        }
    }

    private String joinAttachmentNames(List<MailAttachment> attachments) {
        StringBuilder builder = new StringBuilder();
        for (MailAttachment attachment : attachments) {
            if (builder.length() > 0) {
                builder.append("、");
            }
            builder.append(attachment.getName());
        }
        return builder.toString();
    }

    private String safeMessage(Exception ex) {
        String message = trimToNull(ex.getMessage());
        return message == null ? "请检查SMTP配置和网络连通性" : limit(message, 256);
    }

    private String resolveFailureMessage(Exception ex) {
        if (isAuthenticationFailure(ex)) {
            return "SMTP认证失败，请检查SMTP账号、密码或授权码，并确认邮箱服务已开启SMTP";
        }
        return safeMessage(ex);
    }

    private boolean isAuthenticationFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof MailAuthenticationException) {
                return true;
            }
            String className = current.getClass().getName();
            String message = current.getMessage();
            if (className.contains("AuthenticationFailedException")
                    || (message != null
                            && message.toLowerCase(Locale.ROOT).contains("authentication failed"))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String limit(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().length() == 0) {
            return null;
        }
        return value.trim();
    }
}
