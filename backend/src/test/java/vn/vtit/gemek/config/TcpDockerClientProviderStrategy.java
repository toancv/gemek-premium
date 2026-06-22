/*
 * Copyright (c) 2026 VTIT — Gemek Premium Apartment Management System.
 * All rights reserved.
 */
package vn.vtit.gemek.config;

import org.testcontainers.dockerclient.DockerClientProviderStrategy;
import org.testcontainers.dockerclient.TransportConfig;

import java.net.URI;

/**
 * Forces Testcontainers to connect to Docker Desktop via TCP localhost:2375.
 * Required on Windows where the default named-pipe strategy returns HTTP 400.
 */
public class TcpDockerClientProviderStrategy extends DockerClientProviderStrategy {

    @Override
    public TransportConfig getTransportConfig() {
        return TransportConfig.builder()
                .dockerHost(URI.create("tcp://localhost:2375"))
                .build();
    }

    @Override
    protected boolean isApplicable() {
        return true;
    }

    @Override
    public String getDescription() {
        return "TCP direct (localhost:2375)";
    }
}
