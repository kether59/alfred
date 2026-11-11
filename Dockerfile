FROM eclipse-temurin:21-jre

WORKDIR /app
COPY target/jarvis-core.jar /app/jarvis-core.jar

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50 -XX:+UseSerialGC"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/jarvis-core.jar"]
