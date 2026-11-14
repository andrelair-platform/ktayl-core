# syntax=docker/dockerfile:1

# ---- build stage: Maven + Temurin 21 (self-contained; tests run in CI, skipped here) ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# cache dependencies first (pom only) → faster rebuilds when only src changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests clean package

# ---- runtime stage: Temurin 21 JRE, NON-ROOT (Gatekeeper require-non-root) ----
FROM eclipse-temurin:21-jre-jammy AS runtime
# Trust the minicloud CA in the JVM truststore. The Authentik OIDC issuer (auth.devandre.sbs) resolves
# via split-horizon DNS to the INTERNAL ingress (10.0.0.200), which serves a minicloud-CA cert — the
# resource-server's lazy JWKS fetch TLS-fails without this, so every bearer token 401s. CA injected at
# build via the CA_CERT build-arg (secrets.MINICLOUD_CA_CERT); imported BEFORE dropping to non-root.
ARG CA_CERT=""
RUN if [ -n "$CA_CERT" ]; then \
      printf '%s\n' "$CA_CERT" > /usr/local/share/ca-certificates/minicloud-ca.crt && \
      update-ca-certificates && \
      keytool -importcert -noprompt -trustcacerts -alias minicloud-ca \
        -file /usr/local/share/ca-certificates/minicloud-ca.crt -cacerts -storepass changeit ; \
    fi
# dedicated non-root user (uid 10001) — the pod spec also sets runAsNonRoot/runAsUser + drops caps
RUN groupadd --system app && useradd --system --gid app --uid 10001 app
WORKDIR /app
COPY --from=build /app/target/ktayl-core-*.jar /app/app.jar
USER 10001
EXPOSE 8080
# container-aware JVM defaults; honour JAVA_OPTS for per-env tuning
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
