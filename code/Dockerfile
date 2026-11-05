# ---------- Етап 1: збірка застосунку ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Шар залежностей: перезбирається лише при зміні pom.xml
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Шар коду: компіляція, модульні тести та пакування у виконуваний JAR
COPY src ./src
ARG SKIP_TESTS=false
RUN mvn -B package -DskipTests=${SKIP_TESTS}

# ---------- Етап 2: образ для запуску ----------
FROM eclipse-temurin:17-jre
LABEL org.opencontainers.image.title="library-accounting" \
      org.opencontainers.image.description="Інформаційна система обліку бібліотеки" \
      org.opencontainers.image.version="1.0.0"

WORKDIR /app
RUN groupadd --system library && useradd --system --gid library --no-create-home library
COPY --from=build /build/target/library-accounting-1.0.0.jar app.jar
USER library

ENV DB_URL=jdbc:mariadb://db:3306/library_db \
    DB_USERNAME=library_user \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080

# ENTRYPOINT запускає JVM, CMD містить аргументи Spring, які можна замінити при docker run
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar \"$@\"", "--"]
CMD ["--server.port=8080"]
