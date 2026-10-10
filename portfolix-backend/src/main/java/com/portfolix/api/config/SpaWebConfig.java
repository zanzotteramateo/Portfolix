package com.portfolix.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.time.Duration;

/**
 * Sirve el front ya compilado (fase 12, deploy gratis de un solo origen): los archivos quedan en
 * {@code classpath:/static/}, ahí los copia el {@code Dockerfile} de la raíz del repo al compilar
 * (en dev y en los tests no hay nada ahí, así que esto no hace nada: el front se sirve con
 * {@code npm run dev}, como siempre).
 * <p>
 * Cualquier ruta que no sea un archivo real, ni empiece con {@code /api}, devuelve {@code index.html}
 * para que React Router la resuelva del lado del cliente (ej.: recargar {@code /transactions}).
 * Los {@code /api/**} nunca llegan acá: los controllers tienen prioridad y los resuelven antes.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Los archivos de assets/ tienen hash en el nombre (los pone Vite): no cambian nunca.
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic());

        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        // Una ruta de /api que no resolvió ningún controller es un 404 de verdad,
                        // no el index.html del front.
                        if (resourcePath.startsWith("api/")) {
                            return null;
                        }
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}
