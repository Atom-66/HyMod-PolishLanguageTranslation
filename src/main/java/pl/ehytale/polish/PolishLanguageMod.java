package pl.ehytale.polish;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

/**
 * PolishLanguageMod - Mod Hytale dodający opcję języka polskiego (pl-PL) oraz polskie czcionki bez nadpisywania języka angielskiego (en-US).
 * Autor: AT0MS
 */
public class PolishLanguageMod extends JavaPlugin {
    private static PolishLanguageMod instance;
    private final JavaPluginInit pluginInit;

    private static final String[] LANG_FILES = {
        "client.lang",
        "client_no_translation.lang",
        "meta.lang"
    };

    private static final String[] FONT_FILES = {
        "Lexend-Bold.json", "Lexend-Bold.png",
        "NotoMono-Regular.json", "NotoMono-Regular.png",
        "NunitoSans-ExtraBold.json", "NunitoSans-ExtraBold.png",
        "NunitoSans-Medium.json", "NunitoSans-Medium.png"
    };

    public PolishLanguageMod(@Nonnull JavaPluginInit init) {
        super(init);
        this.pluginInit = init;
        instance = this;
        try {
            ensureClientSharedAssets();
        } catch (Throwable ignored) {}
    }

    public static PolishLanguageMod getInstance() {
        return instance;
    }

    @Override
    protected void setup() {
        System.out.println("[HyMod] Inicjalizacja modyfikacji: Polish Language Translation (Autor: AT0MS)...");
        ensureClientSharedAssets();
        injectRuntimeTranslationsReflectively();
        System.out.println("[HyMod] Dodano język Polski (pl-PL) oraz polskie czcionki (język angielski en-US pozostaje nienaruszony).");
    }

    @Override
    protected void start() {
        ensureClientSharedAssets();
        injectRuntimeTranslationsReflectively();
    }

    @Override
    protected void shutdown() {
        instance = null;
    }

    /**
     * Wyszukuje katalog zasobów współdzielonych klienta Hytale (Client/Data/Shared)
     * i dodaje w nim opcję języka polskiego (Language/pl-PL) oraz polskie czcionki,
     * nie nadpisując oryginalnego języka angielskiego (Language/en-US).
     */
    private void ensureClientSharedAssets() {
        try {
            List<Path> targets = findClientSharedTargets();
            for (Path sharedDir : targets) {
                // Przywróć oryginalny en-US, jeśli wcześniej istniały pliki .bak
                restoreEnglishIfBackedUp(sharedDir);

                // Dodaj wyłącznie język polski (pl-PL) - te same 3 pliki co w en-US
                Path plDir = sharedDir.resolve("Language").resolve("pl-PL");
                Files.createDirectories(plDir);
                Files.deleteIfExists(plDir.resolve("server.lang"));
                for (String file : LANG_FILES) {
                    extractResource("/Language/" + file, plDir.resolve(file));
                }

                // Zainstaluj czcionki obsługujące polskie znaki diakrytyczne
                Path fontsDir = sharedDir.resolve("Fonts");
                Files.createDirectories(fontsDir);
                for (String file : FONT_FILES) {
                    Path dest = fontsDir.resolve(file);
                    backupOnce(dest);
                    extractResource("/Fonts/" + file, dest);
                }
                System.out.println("[HyMod] Zainstalowano język polski (pl-PL) w: " + plDir);
            }
        } catch (Throwable ignored) {}
    }

    private void restoreEnglishIfBackedUp(Path sharedDir) {
        try {
            Path enDir = sharedDir.resolve("Language").resolve("en-US");
            if (Files.isDirectory(enDir)) {
                for (String file : LANG_FILES) {
                    Path bak = enDir.resolve(file + ".bak");
                    Path orig = enDir.resolve(file);
                    if (Files.exists(bak)) {
                        Files.move(bak, orig, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            Path rootLangDir = sharedDir.resolve("Language");
            if (Files.isDirectory(rootLangDir)) {
                for (String file : new String[]{"client.lang", "client_no_translation.lang", "meta.lang", "server.lang", "version.txt", "changelog.txt"}) {
                    Files.deleteIfExists(rootLangDir.resolve(file));
                }
            }
        } catch (Throwable ignored) {}
    }

    private void backupOnce(Path target) {
        try {
            if (Files.exists(target)) {
                Path bak = target.resolveSibling(target.getFileName().toString() + ".bak");
                if (!Files.exists(bak)) {
                    Files.copy(target, bak, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        } catch (Throwable ignored) {}
    }

    private List<Path> findClientSharedTargets() {
        Set<Path> targets = new LinkedHashSet<>();
        Set<Path> searchRoots = new LinkedHashSet<>();

        // 1. Aktywne procesy HytaleClient / HytaleServer
        try {
            ProcessHandle.allProcesses().forEach(ph -> {
                try {
                    Optional<String> cmdOpt = ph.info().command();
                    if (cmdOpt.isPresent()) {
                        String cmd = cmdOpt.get();
                        if (cmd.toLowerCase().contains("hytale")) {
                            Path exePath = Paths.get(cmd).toAbsolutePath().normalize();
                            addAncestors(searchRoots, exePath.getParent(), 6);
                        }
                    }
                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}

        // 2. Ścieżki z JavaPluginInit oraz JavaPlugin
        try {
            if (pluginInit != null) {
                addAncestors(searchRoots, invokePathGetter(pluginInit, "getFile"), 6);
                addAncestors(searchRoots, invokePathGetter(pluginInit, "getDataDirectory"), 6);
            }
            addAncestors(searchRoots, invokePathGetter(this, "getFile"), 6);
            addAncestors(searchRoots, invokePathGetter(this, "getDataDirectory"), 6);
        } catch (Throwable ignored) {}

        // 3. Lokalizacja pliku JAR moda
        try {
            if (PolishLanguageMod.class.getProtectionDomain() != null
                    && PolishLanguageMod.class.getProtectionDomain().getCodeSource() != null) {
                URI uri = PolishLanguageMod.class.getProtectionDomain().getCodeSource().getLocation().toURI();
                addAncestors(searchRoots, Paths.get(uri).toAbsolutePath().normalize(), 6);
            }
        } catch (Throwable ignored) {}

        // 4. Bieżący katalog roboczy
        try {
            addAncestors(searchRoots, Paths.get("").toAbsolutePath().normalize(), 6);
        } catch (Throwable ignored) {}

        // 5. Standardowe ścieżki systemowe (Linux, Flatpak, Windows, macOS)
        try {
            String userHome = System.getProperty("user.home", "");
            if (!userHome.isEmpty()) {
                Path home = Paths.get(userHome);
                searchRoots.add(home.resolve(".local/share/Hytale"));
                searchRoots.add(home.resolve(".var/app/com.hypixel.HytaleLauncher/data/Hytale"));
                searchRoots.add(home.resolve("Library/Application Support/Hytale"));
                searchRoots.add(home.resolve("AppData/Roaming/Hytale"));
                searchRoots.add(home.resolve("AppData/Local/Hytale"));
            }
            String appData = System.getenv("APPDATA");
            if (appData != null && !appData.isEmpty()) {
                searchRoots.add(Paths.get(appData).resolve("Hytale"));
            }
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null && !localAppData.isEmpty()) {
                searchRoots.add(Paths.get(localAppData).resolve("Hytale"));
            }
            for (String winRoot : new String[]{"C:/Program Files/Hytale", "C:/Hytale", "D:/Hytale", "E:/Hytale"}) {
                searchRoots.add(Paths.get(winRoot));
            }
        } catch (Throwable ignored) {}

        // Sprawdź wszystkie możliwe podścieżki w każdym katalogu bazowym
        for (Path root : searchRoots) {
            if (root == null || !Files.exists(root)) continue;
            checkAndAdd(targets, root.resolve("Data/Shared"));
            checkAndAdd(targets, root.resolve("Client/Data/Shared"));
            checkAndAdd(targets, root.resolve("install/release/package/game/latest/Client/Data/Shared"));
            checkAndAdd(targets, root.resolve("install/pre-release/package/game/latest/Client/Data/Shared"));

            Path installDir = root.resolve("install");
            if (Files.isDirectory(installDir)) {
                try (Stream<Path> walk = Files.walk(installDir, 6)) {
                    walk.filter(Files::isDirectory)
                        .filter(p -> p.endsWith(Paths.get("Client", "Data", "Shared")))
                        .forEach(p -> checkAndAdd(targets, p));
                } catch (Throwable ignored) {}
            }
        }

        return new ArrayList<>(targets);
    }

    private Path invokePathGetter(Object target, String methodName) {
        try {
            Method m = target.getClass().getMethod(methodName);
            Object res = m.invoke(target);
            if (res instanceof Path) {
                return ((Path) res).toAbsolutePath().normalize();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private void addAncestors(Set<Path> roots, Path start, int levels) {
        Path cur = start;
        for (int i = 0; i <= levels && cur != null; i++) {
            roots.add(cur);
            cur = cur.getParent();
        }
    }

    private void checkAndAdd(Set<Path> set, Path p) {
        try {
            if (p != null && Files.isDirectory(p)) {
                set.add(p.toAbsolutePath().normalize());
            }
        } catch (Throwable ignored) {}
    }

    private void extractResource(String resPath, Path dest) {
        try (InputStream in = getClass().getResourceAsStream(resPath)) {
            if (in != null) {
                if (dest.getParent() != null) {
                    Files.createDirectories(dest.getParent());
                }
                Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Wstrzyknięcie tłumaczeń wyłącznie dla lokalizacji pl-PL do I18nModule w pamięci serwera Hytale.
     */
    @SuppressWarnings("unchecked")
    private void injectRuntimeTranslationsReflectively() {
        try {
            Class<?> i18nClass = Class.forName("com.hypixel.hytale.server.core.modules.i18n.I18nModule");
            Method getMethod = i18nClass.getMethod("get");
            Object i18nInstance = getMethod.invoke(null);
            if (i18nInstance == null) return;

            Properties props = new Properties();
            for (String res : new String[]{"/Language/server.lang", "/Language/client.lang", "/Language/client_no_translation.lang"}) {
                try (InputStream in = getClass().getResourceAsStream(res)) {
                    if (in != null) {
                        props.load(new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)));
                    }
                } catch (Throwable ignored) {}
            }

            for (java.lang.reflect.Field f : i18nClass.getDeclaredFields()) {
                f.setAccessible(true);
                Object val = f.get(i18nInstance);
                if (val instanceof Map) {
                    Map<Object, Object> map = (Map<Object, Object>) val;
                    Object sub = map.get("pl-PL");
                    if (sub instanceof Map) {
                        Map<String, String> langMap = (Map<String, String>) sub;
                        for (String key : props.stringPropertyNames()) {
                            langMap.put(key, props.getProperty(key));
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }
}

