package com.ailog.mcp.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mcp")
public class McpSecurityProperties {

    private Docker docker = new Docker();
    private Rustfs rustfs = new Rustfs();

    public Docker getDocker() {
        return docker;
    }

    public void setDocker(Docker docker) {
        this.docker = docker;
    }

    public Rustfs getRustfs() {
        return rustfs;
    }

    public void setRustfs(Rustfs rustfs) {
        this.rustfs = rustfs;
    }

    public static class Docker {
        private boolean restartEnabled;

        public boolean isRestartEnabled() {
            return restartEnabled;
        }

        public void setRestartEnabled(boolean restartEnabled) {
            this.restartEnabled = restartEnabled;
        }
    }

    public static class Rustfs {
        private boolean deleteEnabled;

        public boolean isDeleteEnabled() {
            return deleteEnabled;
        }

        public void setDeleteEnabled(boolean deleteEnabled) {
            this.deleteEnabled = deleteEnabled;
        }
    }
}
