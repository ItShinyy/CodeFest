// Main.java

import io.socket.emitter.Emitter;
import jsclub.codefest.sdk.Hero;
import java.io.IOException;

public class Main {
    // Thông tin kết nối server
    private static final String SERVER_URL = "https://cf25-server.jsclub.dev";
    private static final String GAME_ID = "144581";
    private static final String PLAYER_NAME = "CF25_6_Game";
    private static final String SECRET_KEY = "sk-app9hNFHRi-Jhmb-Wdxc-w:ujXkY7oEtvFVJ0lwAP4_QgcBwuVxEqjegauj4Ribw7zFzN6KSjvDRe3OiEX7fNzMTa5WZzMmn0ZS4D9Utit2Mw";


    public static void main(String[] args) throws IOException {
        // Khởi tạo Hero và Listener
        Hero hero = new Hero(GAME_ID, PLAYER_NAME, SECRET_KEY);
        Emitter.Listener onMapUpdate = new MapUpdateListener(hero);

        // Đăng ký Listener và bắt đầu kết nối
        hero.setOnMapUpdate(onMapUpdate);
        hero.start(SERVER_URL);
    }
}