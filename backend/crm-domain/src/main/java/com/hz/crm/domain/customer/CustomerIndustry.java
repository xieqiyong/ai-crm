package com.hz.crm.domain.customer;

import lombok.Getter;

@Getter
public enum CustomerIndustry {

    AGRICULTURE_FORESTRY_FISHERY("农、林、牧、渔业"),
    MINING("采矿业"),
    MANUFACTURING("制造业"),
    UTILITIES("电力、热力、燃气及水生产和供应业"),
    CONSTRUCTION("建筑业"),
    WHOLESALE_RETAIL("批发和零售业"),
    TRANSPORT_STORAGE_POSTAL("交通运输、仓储和邮政业"),
    ACCOMMODATION_CATERING("住宿和餐饮业"),
    INFORMATION_SOFTWARE("信息传输、软件和信息技术服务业"),
    FINANCE("金融业"),
    REAL_ESTATE("房地产业"),
    LEASING_BUSINESS_SERVICES("租赁和商务服务业"),
    SCIENTIFIC_TECHNICAL_SERVICES("科学研究和技术服务业"),
    WATER_ENVIRONMENT_PUBLIC_FACILITIES("水利、环境和公共设施管理业"),
    RESIDENT_SERVICES("居民服务、修理和其他服务业"),
    EDUCATION("教育"),
    HEALTH_SOCIAL_WORK("卫生和社会工作"),
    CULTURE_SPORTS_ENTERTAINMENT("文化、体育和娱乐业"),
    PUBLIC_ADMIN_SOCIAL_ORGANIZATIONS("公共管理、社会保障和社会组织"),
    INTERNATIONAL_ORGANIZATIONS("国际组织"),
    OTHER("其他");

    private final String value;

    CustomerIndustry(String value) {
        this.value = value;
    }

    public static boolean supports(String value) {
        if (value == null) {
            return false;
        }
        for (CustomerIndustry industry : values()) {
            if (industry.value.equals(value)) {
                return true;
            }
        }
        return false;
    }
}
