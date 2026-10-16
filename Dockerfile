FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

COPY src/ src/
RUN ./mvnw package -DskipTests -B \
    && cp target/*.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --destination extracted

FROM eclipse-temurin:17-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /app/extracted/ ./

# Execução de treino do CDS: sobe o contexto sem banco e sai no refresh,
# gravando as classes carregadas em app.jsa. Os valores abaixo valem só para
# esse treino; a execução real usa as variáveis do ambiente.
RUN java -XX:ArchiveClassesAtExit=app.jsa -Dspring.context.exit=onRefresh \
      -Dspring.profiles.active=prod \
      -Dspring.flyway.enabled=false \
      -Dspring.jpa.hibernate.ddl-auto=none \
      -Dspring.jpa.properties.hibernate.boot.allow_jdbc_metadata_access=false \
      -Dspring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect \
      -DDB_PASSWORD=cds-training \
      -DJWT_SECRET=cds-training-only-not-a-secret-0000000000 \
      -DPAYOUT_ENCRYPTION_KEY=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA= \
      -DPROXY_SHARED_SECRET=cds-training-only-not-a-secret-0000000000 \
      -jar app.jar
USER app

EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
# TieredStopAtLevel=1: só o compilador C1, que sobe mais rápido e usa menos
# memória com 0,1 CPU (plano grátis do Render).
ENTRYPOINT ["java", "-XX:SharedArchiveFile=app.jsa", "-XX:TieredStopAtLevel=1", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]

ENV TZ=America/Sao_Paulo
