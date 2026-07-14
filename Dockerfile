# ============================================================
# Stage 1 – Build de l'application (Java 17 d'après ton pom.xml)
# ============================================================
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /workspace

# Copie des fichiers de configuration Maven pour mettre les dépendances en cache
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .
RUN chmod +x mvnw

# Télécharge les dépendances (évite de tout retélécharger à chaque build)
RUN ./mvnw dependency:go-offline -B

# Copie du code source et build
COPY src ./src
RUN ./mvnw package -DskipTests -B && \
    java -Djarmode=layertools -jar target/*.jar extract --destination target/extracted

# ============================================================
# Stage 2 – Runtime léger et sécurisé
# ============================================================
FROM eclipse-temurin:17-jre-alpine AS runtime

# Création d'un utilisateur non-root pour la sécurité en production
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app

# Dossier pour les logs du switch
RUN mkdir -p /app/logs && chown -R appuser:appgroup /app

USER appuser

# Récupération des layers extraits du Stage 1
COPY --from=builder /workspace/target/extracted/dependencies/ ./
COPY --from=builder /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=builder /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=builder /workspace/target/extracted/application/ ./

# Port exposé par ton TnbSwitch (server.port=8090 dans ta conf)
EXPOSE 8090

# Vérification de la santé du conteneur via l'actuator
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8090/actuator/health || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]