FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S nexacart && adduser -S nexacart -G nexacart
WORKDIR /app
COPY --from=build /workspace/target/nexacart-1.0.0.jar app.jar
RUN chown -R nexacart:nexacart /app
USER nexacart
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
