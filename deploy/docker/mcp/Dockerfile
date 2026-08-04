FROM docker.1ms.run/eclipse-temurin:21-jre

WORKDIR /app

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS=""
ENV CRM_MCP_PORT=8091
ENV CRM_LOG_PATH=/app/logs

COPY app.jar /app/app.jar

EXPOSE 8091

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
