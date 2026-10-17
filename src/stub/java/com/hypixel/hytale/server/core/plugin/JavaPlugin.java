package com.hypixel.hytale.server.core.plugin;

import java.nio.file.Path;
import javax.annotation.Nonnull;

public abstract class JavaPlugin extends PluginBase {
    public JavaPlugin(@Nonnull JavaPluginInit init) {
    }

    public Path getFile() {
        return null;
    }
}
