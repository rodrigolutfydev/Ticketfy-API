# ==========================================
# STAGE 1 — BUILD MAVEN
# ==========================================

FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

# Copiar Maven Wrapper
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Preparar Maven
RUN chmod +x mvnw

# Baixar dependências
RUN ./mvnw dependency:go-offline -B

# Copiar código-fonte
COPY src/ src/

# Compilar aplicação
RUN ./mvnw package -DskipTests -B


# ==========================================
# STAGE 2 — RUNTIME
# ==========================================

FROM eclipse-temurin:17-jre

WORKDIR /app

# Criar usuário sem privilégios
RUN groupadd --system app \
    && useradd --system --gid app app

# Copiar aplicação compilada
COPY --from=build /app/target/*.jar /app/app.jar

# Definir permissões
RUN chown app:app /app/app.jar

# Executar como usuário não-root
USER app

# ==========================================
# CONFIGURAÇÕES
# ==========================================

ENV SPRING_PROFILES_ACTIVE=prod
ENV TZ=America/Sao_Paulo

EXPOSE 8080

# ==========================================
# INICIALIZAÇÃO
# ==========================================

ENTRYPOINT ["java", "-XX:TieredStopAtLevel=1", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]