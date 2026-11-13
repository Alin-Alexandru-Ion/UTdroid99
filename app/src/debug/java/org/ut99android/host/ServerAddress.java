package org.ut99android.host;

/** Strict input for a UT server URL; the result is passed as an argv element, never a shell command. */
final class ServerAddress {
    private ServerAddress() { }
    static String parse(String input) {
        if (input == null) throw new IllegalArgumentException("Enter a server address.");
        String value = input.trim();
        if (value.regionMatches(true, 0, "unreal://", 0, 9)) value = value.substring(9);
        if (value.length() == 0 || value.length() > 259 || !value.matches("[A-Za-z0-9.-]+(?::[0-9]{1,5})?"))
            throw new IllegalArgumentException("Use a hostname or IPv4 address, optionally followed by :port.");
        int colon = value.indexOf(':');
        String host = colon < 0 ? value : value.substring(0, colon);
        if (host.length() > 253 || host.startsWith(".") || host.endsWith(".") || host.contains(".."))
            throw new IllegalArgumentException("Enter a valid hostname or IPv4 address.");
        for (String label : host.split("\\.")) {
            if (label.length() > 63 || label.startsWith("-") || label.endsWith("-"))
                throw new IllegalArgumentException("Enter a valid hostname or IPv4 address.");
        }
        if (colon >= 0) {
            int port = Integer.parseInt(value.substring(colon + 1));
            if (port < 1 || port > 65535) throw new IllegalArgumentException("Port must be between 1 and 65535.");
        }
        return "unreal://" + value;
    }
}
