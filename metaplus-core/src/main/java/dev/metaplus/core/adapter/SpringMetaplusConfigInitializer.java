package dev.metaplus.core.adapter;

import dev.metaplus.core.config.GlobalConfig;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;

public class SpringMetaplusConfigInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        ConfigurableEnvironment env = applicationContext.getEnvironment();
        GlobalConfig.loadSpringEnv(env, "metaplus.");
    }

}
