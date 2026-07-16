package com.ailog.rootcause.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "rootcause")
public class RuleConfigProperties {

    private Map<String, RuleConfig> rules = new LinkedHashMap<>();

    public Map<String, RuleConfig> getRules() { return rules; }
    public void setRules(Map<String, RuleConfig> rules) { this.rules = rules; }

    public static class RuleConfig {
        private boolean enabled = true;
        private int priority = 5;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }
    }
}