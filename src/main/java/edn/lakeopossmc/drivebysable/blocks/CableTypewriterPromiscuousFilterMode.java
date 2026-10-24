package edn.lakeopossmc.drivebysable.blocks;

public enum CableTypewriterPromiscuousFilterMode {
    BLACKLIST("blacklist", 0),
    WHITELIST("whitelist", 1);

    private final String name;
    private final Integer id;

    CableTypewriterPromiscuousFilterMode(String name, Integer id) {
        this.name = name;
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public Integer getId() {
        return id;
    }

    public static CableTypewriterPromiscuousFilterMode fromName(final String name) {
        for (final CableTypewriterPromiscuousFilterMode mode : values()) {
            if (mode.getName().equals(name)) {
                return mode;
            }
        }
        return null;
    }

    public static CableTypewriterPromiscuousFilterMode fromId(final Integer id) {
        for (final CableTypewriterPromiscuousFilterMode mode : values()) {
            if (mode.getId().equals(id)) {
                return mode;
            }
        }
        return null;
    }
}
