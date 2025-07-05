// Main.java

import io.socket.emitter.Emitter;
import jsclub.codefest.sdk.Hero;
import java.io.IOException;

public class Main {
    // Thông tin kết nối server
    private static final String SERVER_URL = "https://cf25-server.jsclub.dev";
    private static final String GAME_ID = "159031";
    private static final String PLAYER_NAME = "CF25_6_Game";
    private static final String SECRET_KEY = "sk-2HYKEtbfRFmEqLGQ936Wtg:v_n5V_7aApxskmtzo0hcfL3Nb7WGED9Xu2H69EGtiig0unKU2VIVRvnTG8olDNhIIqRHKp7yFn5PlKTe7wXXlw";


    public static void main(String[] args) throws IOException {
        // Khởi tạo Hero và Listener
        Hero hero = new Hero(GAME_ID, PLAYER_NAME, SECRET_KEY);
        Emitter.Listener onMapUpdate = new MapUpdateListener(hero);

        // Đăng ký Listener và bắt đầu kết nối
        hero.setOnMapUpdate(onMapUpdate);
        hero.start(SERVER_URL);
    }
}