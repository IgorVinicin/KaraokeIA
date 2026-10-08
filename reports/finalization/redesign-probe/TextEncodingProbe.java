import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** Runs against the compiled production helper, without copying its implementation. */
public final class TextEncodingProbe {
    public static void main(String[] args) throws Exception {
        Class<?> type = Class.forName("com.araujo.karaoke_ia.online.OnlineRepository$Companion");
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object companion = constructor.newInstance();
        Method repair = type.getDeclaredMethod("repairStoredText", String.class);
        repair.setAccessible(true);
        String[][] cases = {
            {"Karaok\u00c3\u00aa - Tipo Minato \u00e2\u0161\u00a1", "Karaok\u00ea - Tipo Minato \u26a1"},
            {"Jo\u00c3\u00a3o", "Jo\u00e3o"},
            {"Karaok\u00c3\u0192\u00c2\u00aa", "Karaok\u00ea"},
            {"Jo\u00e3o", "Jo\u00e3o"},
            {"Anitta \ud83c\udfa4", "Anitta \ud83c\udfa4"},
            {"\u00c2me", "\u00c2me"},
            {"\u4e2d\u6587", "\u4e2d\u6587"},
            {"\u00c3x", "\u00c3x"},
            {"Andr\u00e9 \u2013 \u00c2me", "Andr\u00e9 \u2013 \u00c2me"}
        };
        for (int i = 0; i < cases.length; i++) {
            String actual = (String) repair.invoke(companion, cases[i][0]);
            if (!cases[i][1].equals(actual)) throw new AssertionError("Encoding case " + i);
        }
        System.out.println("PASS: " + cases.length + " encoding cases against compiled production code");
    }
}
