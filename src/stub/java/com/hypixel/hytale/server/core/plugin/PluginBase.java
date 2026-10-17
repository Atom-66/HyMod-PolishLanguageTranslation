package com.hypixel.hytale.server.core.plugin;

import java.nio.file.Path;

public abstract class PluginBase {
    protected void setup() {}
    protected void start() {}
    protected void shutdown() {}
    public Path getDataDirectory() { return null; }
}

