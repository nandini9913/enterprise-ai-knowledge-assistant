package com.nandini.knowledgeassistant.user;

import com.nandini.knowledgeassistant.config.SecurityProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Creates the first administrator from environment configuration, if requested.
 * Public registration only ever creates {@link Role#USER} accounts.
 */
@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final SecurityProperties properties;
    private final UserRepository users;
    private final UserService userService;

    public BootstrapAdminInitializer(SecurityProperties properties, UserRepository users, UserService userService) {
        this.properties = properties;
        this.users = users;
        this.userService = userService;
    }

    @Override
    public void run(ApplicationArguments args) {
        SecurityProperties.BootstrapAdmin admin = properties.bootstrapAdmin();
        if (admin == null || !admin.isConfigured() || users.existsByUsernameIgnoreCase(admin.username())) {
            return;
        }
        userService.register(admin.username(), null, admin.password(), Role.ADMIN);
        log.info("Created bootstrap administrator '{}'", admin.username());
    }
}
