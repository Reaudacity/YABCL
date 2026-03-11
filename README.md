# Introduction

**YABCL** (*Yet Another Boring Config Library*) is a modern configuration framework for Minecraft plugins designed to eliminate the repetitive boilerplate that comes with managing YAML config files.

The library gives you **two ways to define configs** — an annotation-based approach that maps Java fields directly to YAML paths, and a fluent builder API for cases where you prefer a fully programmatic style. Both approaches run the same underlying pipeline: file creation, default values, version tracking, automatic migration, and reload support.

YABCL was built with clean developer ergonomics in mind, so you spend time writing plugin logic instead of parsing YAML by hand.

---

## Features

* **Two APIs, one pipeline**
  Use annotations or the fluent builder — whichever fits your style. Both support every feature.

* **Annotation-based config classes**
  Map Java fields to YAML paths with `@ConfigField`, group them into sections with `@ConfigSection`, and declare the file with `@ConfigVersion`.

* **Fluent builder API**
  Define configs entirely in code using `ConfigBuilder.create(...)` — no subclassing required.

* **Automatic version migration**
  Declare migration steps with `@ConfigMigration` (annotation API) or `.migrate(...)` (builder API). YABCL runs them automatically when it detects an outdated file.

* **Default values and required fields**
  Declare defaults inline. Mark fields as `required = true` to throw an error when a key is absent with no fallback.

* **Nested section support**
  Group related keys into plain Java objects with `@ConfigSection`. Sections can be nested as deep as you need.

* **Reload-all support**
  Every config is registered automatically. Call `YabclConfig.reloadAll()` from your reload command to refresh everything at once.

* **File header and inline comments**
  Write comments above keys and a header at the top of the file so generated configs are self-documenting out of the box.

---

## Before and After

One of YABCL's primary goals is to cut out the boilerplate that piles up when managing configs the traditional way.

---

### Without YABCL

Reading config values the standard Bukkit way means calling `getConfig()` everywhere, handling missing keys manually, and scattering default values across your codebase.

```java
public class MyPlugin extends JavaPlugin {

    private String prefix;
    private int maxHomes;
    private String dbHost;
    private int dbPort;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        prefix   = getConfig().getString("general.prefix", "<gray>[Plugin]</gray>");
        maxHomes = getConfig().getInt("limits.max-homes", 5);
        dbHost   = getConfig().getString("database.host", "localhost");
        dbPort   = getConfig().getInt("database.port", 3306);

        if (!getConfig().isSet("database.password")) {
            getLogger().severe("database.password is not set! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
    }
}
```

Even for a small config this means:

* Defaults scattered across multiple `get` calls
* Manual required-field checks
* No structure for related keys
* No version tracking or migration

As the config grows, this becomes increasingly painful to maintain.

---

### With YABCL

The same config defined using the annotation API:

```java
@ConfigVersion(file = "config.yml", version = "1.0")
public class MyConfig extends YabclConfig {

    @ConfigField(path = "general.prefix", defaultValue = "<gray>[Plugin]</gray>")
    public String prefix;

    @ConfigField(path = "limits.max-homes", defaultValue = "5")
    public int maxHomes;

    @ConfigSection(path = "database")
    public DatabaseSection database;

    public static class DatabaseSection {
        @ConfigField(path = "host", defaultValue = "localhost")
        public String host;

        @ConfigField(path = "port", defaultValue = "3306")
        public int port;

        @ConfigField(path = "password", required = true)
        public String password;
    }
}
```

Loading it in `onEnable`:

```java
ConfigLoadResult<MyConfig> result = YabclConfig.load(this, MyConfig.class);

if (result.isFailed()) {
    getLogger().severe("Config failed: " + result.getError());
    getServer().getPluginManager().disablePlugin(this);
    return;
}

MyConfig config = result.require();
```

YABCL automatically handles:

* File creation and default values
* Required field validation
* Nested section instantiation
* Version stamping and migration
* Reload-all registration

---

### Result

With YABCL:

* Defaults live next to the field they belong to
* Required fields are declared, not checked manually
* Related keys are grouped into typed section objects
* Version history is tracked and migrations run automatically
* A single `YabclConfig.reloadAll()` refreshes everything

As your config grows, the structure scales with it naturally.

---

## Why This Project Exists

YABCL was originally created to support my own projects and provide a cleaner way to manage configuration files in Minecraft plugins.

The standard Bukkit config API works fine for simple cases, but once you have multiple files, nested keys, required fields, and the need to migrate old configs after a schema change, the boilerplate multiplies quickly.

YABCL exists to make config management simple, declarative, and version-safe — whether you prefer annotations or a builder.

---

## Requirements

To use YABCL, your environment must meet the following requirements:

* **Minecraft:** 1.20 or newer
* **Server Software:** Paper — YABCL uses Paper's Adventure API for console logging
* **Java:** Java 17 or newer
* **YABCL:** Must be installed in your `plugins` folder

---

## License

This project is licensed under the **MIT License**.
You are free to use, modify, and distribute this software in accordance with the terms of the license.