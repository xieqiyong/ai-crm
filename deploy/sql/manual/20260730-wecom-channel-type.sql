alter table crm_channel_record
    drop constraint if exists crm_channel_record_channel_type_check;

alter table crm_channel_record
    add constraint crm_channel_record_channel_type_check
    check (channel_type in ('MANUAL', 'FORM', 'AUDIO', 'VIDEO', 'DOCUMENT', 'WECOM'));
