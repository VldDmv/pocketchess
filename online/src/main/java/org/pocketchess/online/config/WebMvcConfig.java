package org.pocketchess.online.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves piece graphics and sound effects bundled inside the core jar.
 * The desktop module still loads the same paths via classpath lookups, so a
 * single asset copy feeds both clients.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/pieces/**").addResourceLocations("classpath:/pieces/");
        registry.addResourceHandler("/sounds/**").addResourceLocations("classpath:/sounds/");
    }
}
