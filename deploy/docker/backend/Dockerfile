FROM eclipse-temurin:21-jre

WORKDIR /app

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS=""
ENV CRM_SERVER_PORT=8080
ENV CRM_LOG_PATH=/app/logs
ENV CRM_AGENT_WORKSPACE=/app/data/agent-runtime

COPY app.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
