# 智能营销管理系统开发约束

## Java 代码规范

- Spring Bean 禁止使用构造器注入，必须使用 `@Autowired` 字段注入。
- Java 行注释、块注释和 Javadoc 禁止使用英文，必须使用中文。
- 虽然使用java21，尽量使用jdk8 的语法

## 架构规范

- Java 根包名统一使用 `com.hz.crm`。
- 按照 DDD 分层组织领域、应用、基础设施和接口代码。
- Agent 能力统一通过独立的 `crm-agent-runtime` 模块接入。

## Maven 多模块工程
- crm-application：事务入口，逻辑层
- crm-web入口层：web 控制器入口，所有请求只能用POST
- crm-domain层： 聚合根，实体，枚举等，负责底层查询，写入
- crm-agent-runtime：agent创建基建层
- crm-agent-web: agent 控制层及入口，agent创建，skills，mcp管理等
- crm-auth: jwt, rbac权限体系及认证登录授权等
- crm-activity: 工作流引擎，可以自己封装，也可以使用第三方
- crm-common: 工具类，id算法（使用雪花递增），日期等
- crm-observability: 审计，日志等可观测的东西
- crm-knowledge: 知识库，rag系统接入层

## 技术栈要求
- java21
- springboot 4.0 版本以上
- pgsql做数据源，jpa保留用于实体DDL初始化和表结构演进；业务逻辑禁止使用JpaRepository和EntityManager做数据读写，业务查询、分页、增删改统一使用mybatis-plus，分页器可以使用pagehelper
- 遵循DDL领域模型
- 引入中间件redis，配置中心nacos，MinIO，Elasticsearch
- 使用fastjson，lombok等接入

## 数据库结构变更约束

- 禁止在应用启动阶段通过 `ApplicationRunner`、`CommandLineRunner`、监听器、初始化 Bean 或其他代码自动修改历史数据库结构。
- 禁止使用 `JdbcTemplate`、MyBatis、JPA 原生 SQL 等方式在业务应用中执行 `ALTER TABLE`、`DROP CONSTRAINT`、字段类型转换等兼容操作。
- 禁止为了兼容历史错误表结构，在业务代码中增加字段截断、旧字段兜底、双写或静默降级逻辑。
- JPA 实体只负责描述当前目标表结构以及全新环境的 DDL 初始化，不负责自动修复存量数据库。
- 存量数据库与当前实体结构不一致时，必须停止自动处理，明确告知开发人员问题原因、影响范围和需要手动执行的 SQL，由开发人员确认后自行处理。
