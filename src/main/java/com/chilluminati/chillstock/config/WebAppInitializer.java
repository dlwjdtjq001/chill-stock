package com.chilluminati.chillstock.config;

import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.session.data.redis.RedisOperationsSessionRepository;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import java.util.EnumSet;

public class WebAppInitializer extends AbstractAnnotationConfigDispatcherServletInitializer {

    @Override
    protected Class<?>[] getRootConfigClasses() {
        return new Class[] {
                ThymeleafConfig.class,
                HikariCPConfig.class,
                MybatisConfig.class,
                RedisConfig.class,
                SecurityConfig.class,
                WebClientConfig.class,
                AppConfig.class};// DB 설정이 따로 있다면 여기에 추가 기타 컨픽도 추가
    }

    @Override
    protected Class<?>[] getServletConfigClasses() {
        return new Class[] {
                WebMvcConfig.class }; // 위 AppConfig 등록
    }

    @Override
    protected String[] getServletMappings() {
        return new String[] { "/" }; // DispatcherServlet 매핑
    }

    @Override
    public void onStartup(ServletContext servletContext) throws ServletException {
        // DispatcherServlet 등록
        super.onStartup(servletContext);

        // 인코딩 필터
        CharacterEncodingFilter characterEncodingFilter = new CharacterEncodingFilter();
        characterEncodingFilter.setEncoding("UTF-8");
        characterEncodingFilter.setForceEncoding(true);
        FilterRegistration.Dynamic encodingFilter = servletContext.addFilter("characterEncodingFilter", characterEncodingFilter);
        encodingFilter.addMappingForUrlPatterns(null, false, "/*");


        // Redis 기반 SpringSession 필터 수동 등록
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.register(RedisConfig.class);
        context.refresh();

        SessionRepositoryFilter sessionRepositoryFilter =
                context.getBean("springSessionRepositoryFilter", SessionRepositoryFilter.class);
        FilterRegistration.Dynamic sessionFilter =
                servletContext.addFilter("springSessionRepositoryFilter", sessionRepositoryFilter);
        sessionFilter.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), false, "/*");
    }
}