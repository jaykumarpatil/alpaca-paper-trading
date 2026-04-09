FROM eclipse-temurin:25-jre
WORKDIR /app
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar
ENTRYPOINT ["java","-XX:+UseZGC","-jar","/app/app.jar"]
