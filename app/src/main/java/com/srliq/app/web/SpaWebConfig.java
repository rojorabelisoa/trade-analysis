package com.srliq.app.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Service de l'interface web (front React) quand elle est embarquée dans le jar
 * ({@code ./gradlew :app:bootJar -PwithWeb} copie {@code web/dist} dans {@code classpath:/static/}).
 *
 * <ul>
 *   <li>{@code /assets/**} : fichiers à nom haché, mis en cache longtemps ;</li>
 *   <li>autres fichiers existants (favicon…) : servis tels quels ;</li>
 *   <li>toute autre route du front ({@code /signaux}, {@code /paper}…) : {@code index.html}, le routeur
 *       du navigateur prend le relais ;</li>
 *   <li>{@code /api/**} et {@code /actuator/**} ne sont jamais remplacés par {@code index.html} : une
 *       route d'API inconnue reste une 404 {@code application/problem+json}.</li>
 * </ul>
 *
 * Sans front embarqué (build sans {@code -PwithWeb}), il n'y a pas d'{@code index.html} : toutes ces
 * routes répondent 404, comme avant.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    private final String location;

    public SpaWebConfig(@Value("${srliq.web.static-location:classpath:/static/}") String location) {
        this.location = location.endsWith("/") ? location : location + "/";
    }

    /** La racine ne passe pas par les résolveurs de ressources (chemin vide) : renvoi explicite. */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/" + SpaFallbackResolver.INDEX);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(location + "assets/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable());
        registry.addResourceHandler("/**")
                .addResourceLocations(location)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new SpaFallbackResolver());
    }

    /** Fichier demandé s'il existe, sinon {@code index.html} pour les routes du front. */
    static final class SpaFallbackResolver extends PathResourceResolver {

        static final String INDEX = "index.html";

        @Override
        protected Resource getResource(String resourcePath, Resource location) throws IOException {
            Resource found = super.getResource(resourcePath, location);
            if (found != null) {
                return found;
            }
            if (!isFrontRoute(resourcePath)) {
                return null;
            }
            return super.getResource(INDEX, location);
        }

        /** Route du routeur React : ni API, ni actuator, ni fichier (dernier segment sans extension). */
        static boolean isFrontRoute(String path) {
            String p = path.startsWith("/") ? path.substring(1) : path;
            if (p.equals("api") || p.startsWith("api/") || p.equals("actuator") || p.startsWith("actuator/")) {
                return false;
            }
            String last = p.substring(p.lastIndexOf('/') + 1);
            return !last.contains(".");
        }
    }
}
