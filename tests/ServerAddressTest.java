package org.ut99android.host;

public final class ServerAddressTest {
    public static void main(String[] args) {
        if (!ServerAddress.parse("  EXAMPLE.net:7777 ").equals("unreal://EXAMPLE.net:7777")) throw new AssertionError();
        if (!ServerAddress.parse("unreal://127.0.0.1").equals("unreal://127.0.0.1")) throw new AssertionError();
        for (String bad : new String[]{"", "-example.com", "example..com", "example.com:0", "example.com:65536", "host?game=x", "host;quit", "[::1]:7777", "host\nother"}) {
            try { ServerAddress.parse(bad); throw new AssertionError("Accepted " + bad); }
            catch (IllegalArgumentException expected) { }
        }
        System.out.println("Server address validation: pass");
    }
}
