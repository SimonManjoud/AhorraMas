package ve.impulsatech.ahorramas.backoffice.adaptador.entrada.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Sirve la UI mínima del backoffice (SPA estática) en /backoffice/. */
@Configuration(proxyBeanMethods = false)
class BackofficeUiConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/backoffice", "/backoffice/");
        registry.addViewController("/backoffice/").setViewName("forward:/backoffice/index.html");
    }
}
