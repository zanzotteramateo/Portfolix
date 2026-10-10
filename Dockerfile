# Imagen de producción combinada, para el deploy gratis de la fase 12 (Render): compila el front y lo
# mete dentro de la imagen del backend, para que los dos se sirvan desde el mismo origen (así la cookie
# SameSite=Strict del refresh token funciona sin necesitar un dominio propio con app./api.). Se construye
# desde la RAÍZ del repo, porque necesita ver portfolix-backend/ y portfolix-frontend/ a la vez:
#   docker build -t portfolix .
#
# Los Dockerfile de portfolix-backend/ y portfolix-frontend/ (para el plan con dos apps en Fly, con
# dominio propio) siguen ahí, sin tocar.

# ---- Etapa 1: compilar el front ----
FROM node:22-alpine AS frontend-build
WORKDIR /front
COPY portfolix-frontend/package.json portfolix-frontend/package-lock.json ./
RUN npm ci
COPY portfolix-frontend/ ./
# Sin VITE_API_URL: el cliente (src/api/client.ts) llama a rutas relativas (/api/v1), mismo origen
# que la API, que es justo lo que hace falta acá.
RUN npm run build

# ---- Etapa 2: compilar el backend, con el front ya compilado adentro ----
FROM eclipse-temurin:25-jdk AS backend-build
WORKDIR /build

# Primero solo lo que define las dependencias: mientras el pom.xml no cambie, Docker reusa esta capa.
COPY portfolix-backend/.mvn/ .mvn/
COPY portfolix-backend/mvnw portfolix-backend/pom.xml ./
RUN ./mvnw -B -ntp dependency:go-offline

COPY portfolix-backend/src/ src/
# El front compilado queda como recurso estático de Spring Boot: lo sirve SpaWebConfig (config/).
COPY --from=frontend-build /front/dist/ src/main/resources/static/

# Sin tests: los corre el CI, y adentro de un build de Docker no hay Docker para Testcontainers.
RUN ./mvnw -B -ntp package -DskipTests \
    && cp target/*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---- Etapa 3: imagen final ----
FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 portfolix
WORKDIR /app

# El jar separado en capas, de la que menos cambia a la que más.
COPY --from=backend-build /build/extracted/dependencies/ ./
COPY --from=backend-build /build/extracted/spring-boot-loader/ ./
COPY --from=backend-build /build/extracted/snapshot-dependencies/ ./
COPY --from=backend-build /build/extracted/application/ ./

# Nunca como root.
USER portfolix
EXPOSE 8080

# La JVM usa hasta el 75% de la memoria del contenedor.
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "application.jar"]
