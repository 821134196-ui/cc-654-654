package org.scholarship.web;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.scholarship.service.DemoSeedService;

@ApplicationScoped
public class StartupLifecycle {

    @Inject
    DemoSeedService seedService;

    void onStart(@Observes StartupEvent ev) {
        seedService.seedUsersIfEmpty();
    }
}
