package com.fms.consumer.config;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.shared.communication.PushMode;

/**
 * Application shell configuration for Vaadin.
 * Configures Push at the application level as required by Vaadin 24+.
 */
@Push(PushMode.AUTOMATIC)
public class AppShellConfig implements AppShellConfigurator {
}
