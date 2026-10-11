FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --create-home spring
COPY --from=build /app/target/*.jar app.jar
COPY scripts/start-server.sh start-server.sh
RUN chown spring:spring app.jar start-server.sh && chmod +x start-server.sh
USER spring
ENV PORT=10000
ENV TZ=Asia/Dhaka
EXPOSE 10000
ENTRYPOINT ["./start-server.sh"]
