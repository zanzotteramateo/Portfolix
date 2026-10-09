# Imagen de producción de la API (fase 10B). Se construye desde portfolix-backend/:
#   docker build -t portfolix-backend .
# Dos etapas: la primera compila con el JDK; la imagen final solo lleva el JRE y la app.

# ---- Etapa 1: compilar ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build

# Primero solo lo que define las dependencias: mientras el pom.xml no cambie, Docker reusa esta capa
# y no las vuelve a descargar en cada build.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B -ntp dependency:go-offline

# Sin tests: los corre el CI, y adentro de un build de Docker no hay Docker para Testcontainers.
COPY src/ src/
RUN ./mvnw -B -ntp package -DskipTests \
    && cp target/*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---- Etapa 2: imagen final ----
FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 portfolix
WORKDIR /app

# El jar separado en capas, de la que menos cambia a la que más: un cambio en el código solo
# reemplaza la última (unos KB), no los ~70 MB de dependencias.
COPY --from=build /build/extracted/dependencies/ ./
COPY --from=build /build/extracted/spring-boot-loader/ ./
COPY --from=build /build/extracted/snapshot-dependencies/ ./
COPY --from=build /build/extracted/application/ ./

# Nunca como root: si alguien lograra ejecutar código en la app, no tendría permisos sobre el contenedor.
USER portfolix
EXPOSE 8080

# La JVM usa hasta el 75% de la memoria del contenedor (el resto queda para el código nativo, los hilos, etc.).
# Para cambiarlo en el servidor, redefinir la variable.
ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "application.jar"]
