package dev.vibe.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

/**
 * The shipped first-run profile compiled into the client.  It is never copied
 * to the player configuration directory: VibeConfig reads it directly on a
 * clean installation and normal saved profiles remain independent of it.
 */
final class DefaultModuleProfile {
    private static final String DATA =
            "H4sIAAAAAAAACr1dW3PbOJZ+z69AaWqreqq6vbpbdp50tT1txy5TiXf28gCTkIQWSKhB0I4z1f99CwBJASBA0QkzfukW8fHgds7BuYH51wcAOhvKYsg7l2Dw" +
            "q/h5YHSDCepcgk6ENjAj/NPyqSObQoYgp0w0zfABptpTFE0FiU6/2x//1r34rdcFveHlqH856itUCl88mPPL/kBhYMZ3lKWdS/A/HwAARS8fAPg/2b7Los4l" +
            "+JdqI/CNZvwLYimmSecS9AUEgM4r5IjFkO1LKACdr51LcKEAAHTejF8hSjhDgvIGkhSVz18Q4ziEhLzNTYQE/JV3BxmDbwSn/N/TXUgpi3ACOUrrOxz2W+ox" +
            "DSlDzxSyqL7D7nf1x1lmTZDQ8MTWnXfbmhpKBfPgZENP7N64pR5jyjFNtgwedvU99vqtTZJDskfsRHcXbbELZLHUEP8GWcDJC0o4ZW8nJKGtzdshSHhl37rm" +
            "Qk4cnQkmf6+cH9L6jgbj1jpqwo4XP8yOH/I+OzGNMoKOWr7sFos3Oig9yNNAPkIJfCbVqe2RGFR/VD5IEec42eprBkBnGTyAOxppfeUN/YU4VNSfPFry5/cZ" +
            "JzhBYE6JOub+1l8s+/P+alUOCYDOChNSgQyGOuSBwDfEFEgOQSDXCMb5expUYVZQYeSCGZ3BCIGAQ8b94xGQZSJX72/TyWi0OncAggOSC9npnXX1xlsx4Scc" +
            "Sc7u9M5GeuN6x2i23YEnSIhYxY7YBh3wCcbI2XAthQXMIKtv1bvuOiDFwngoyJU5sV85dJlEGnC1Gs3GSxO4QAc5qHB/IJB7uiURuOEodjUucMphEiLfQoEH" +
            "mmJxAEhuoAfPiuioW7Thri4MkKDtHKSBmlHOaWzwBcMoidL/FIyZgs8pAjnfLpTh59xY86UmHG6+cf+CGMORLmSj2XIyHVs8a76kBLMhWgioBjVF04TqbNSQ" +
            "+pGdvIxkvuCVERfdJrgaFjShdQxpImuZ3jl/n2A6wT4hry5UnYw4SddJi3s1amXHsdb1QrSGbIt4Y/Ep4KbgVGSmgDmkZbXqdnsWw5VwTU5cjFngSglRIFNC" +
            "ClBFNuooNpCKAuqVB5NWPaJGBgpQHfcXmFq+t+bm43gL5uN1ffp1XG6Rq+Nve6a1nG2snYOnc+RfunlYtc1QhEUIwGGhmdagMtG6TgutrgsuR+kxApt3cZy2" +
            "0wC8RgR/1RYHgE6AYxlqqFqFEpwLbK/Gyjii+l5TTPRikBpPVquLmRekKPXGs8loaoLmmIUEnRyWAasb1zaGJ4npKD+tNWanx6WBaigxGJZmtKK1yv+8sL4X" +
            "do2/fXuziA1mi6EfpWgNZstzm5ZpNfdNSZ8mOIZCukyju4GAYY7iFnnftovP5/owtcOi4nZUPSHXitb6DkGcnxuKeIPZ7zAXgTvEfqrs9xfglzmjabqDmP3d" +
            "LfL1015kTG6v3Pph1zJpfHaR9NJgipOtaF7CFInjWocMFqBkHYEJOOQ41BH9BQjwN0l/YnbbX4Da3Rgc3+ye9cZWUy07DxZAefTgCh4Uge65BdB4yZ74sVXj" +
            "pq78m08soNvfbMA66R4RxCFpX3JcDPAIcfJMX52SU7uU9wl5A/lB7Hz7pMPt81EbrFFI0xhxHKYN4yvvP7zLHn62lRDuUNqmkfBJ5EAImAuy7jiHApj29aI3" +
            "tjzFHKaZ1wIzMCTxEUUpp0IavL2VkFP2fAm0DXqjx2USiePR251q1/uaDSYTm+kVquxIQcyOajXQHfxqmOS24iyNWDMiVtkJBPOjviYgpiO8EbEpOewgmHJw" +
            "B7++R46eUdQi681QZDmDT5Qe9RhQv9VgRev4wlw28b7JJsPVdGBOV2A0DhGAobE313i7I3i740Au7wxFlWPz/jnFEYaJJ3Kat9pDWQwX5lBKoD6exXBhjueJ" +
            "UnNWF7PxdGCRkqCSjEKYZK4ITG2nuKrNFUobj/gbGou8TJwCuexOLKu8BGrkBMoc1xeMXg1Z6I/GVSvCzaC14taEe0WCLdzBZIt+oqU5bz4W+oIYgW9tjaTG" +
            "EinMkELQpIpxtD8wHEOWOwFyUsuLQd9mmwIdoJAmkYkfXKxWy4kbr9kGBlMUHFMxHEW6oRhy1SSUzdURKy4eDStQ13BH49VgPhka1tiMIbg37dGb5BWyXORO" +
            "uzt1Vk1jlyDMOHrBaQZJ2iqrggeGUiTdk1v6Yqy3ay0n3fnccoUdyyg02cVFVfUGNFNhMXtrReMsY7J4wN8I5jRL8sqJqu5XkKNt3/cAiq2xuO6IuMUbxHGM" +
            "wC9x+ne5iaOu1Z9m91ZMFtVkeRIj5/ugcKDKrgZduytpKT/tMBHi+pI7TRWBPsAQc7HpnYnV1TWCzG3tqBYQHOBrAh4hP064bw8ihzZYmQV1dyaeu7rquQg0" +
            "6eghE5LzHhHiDP6BQk4ZRq3JUK2TIwy9NQ73qXuiKjiuic1kdj6uGpsofgPXuAg8D/pCARunPySkBIhGG3DFhNiVkNGoevIHO8GxMIlwsgUz6jxkDYyMG5xE" +
            "zfIalirrlWbWMuGYiw1xmuMc8zewZhC7DwRhG4jQgaNtGkVFFuILIrSQjoqfWfFgGzDSJiPkmYnh/zzHbkNflG3SWgxodf/lyIeNTpyU0zgsAkRtDSPgb6qo" +
            "TrHQr22EGgp16jt2NfXYH1n6ccpicIuSrZJgy/pc73C4T5BidYtoHgMamE9FgEhkvqgW8TO1W3lEWdoiD1A/wghnsr9zZ3uAtjFKlJId2AqHviDRWMSnbKHQ" +
            "28E0Lk5T+3CiPA+oVBdaNNUZONeU4W804SJEsNnkhoWl+L7k9TF+RAPTtRJBcxEol9oapTLp1II6LQ6tXduQnrXcBszn7Rsgh7veQBJhYVe2dmxJxSw0dCmN" +
            "wQEn+rBFTrHi+gavlEW6zEqpTdIsFoOxGlaURuaTB1n0Z8Hu+Q4xty992p4u+FEKg8XG/+Xnr3/6m/7bamqyPfEzRsJ/bWlz1sLwKRPiUgsbOgnHyKWqxFvH" +
            "dRrZ1sZbAmMcauavBXhCUOxE2fEnmiBXe8BZqS0bJnNUKvPHoq2a4SdPdafBcEef3dFkyUm6x6VzevKCUyz519m8TShDQJRBxHm1cTXxnTeCBeIoLBhSxhlV" +
            "cLoJF+EYpqmon26JjUpPGccHYkVV/8xE1vGOZikCC/qauBcNx6DQ1eTNaexxBjcI3CSi9F23xvVja4fCvbIEgdSFfoUrQalwdjjKK8mr9DQocCqpBxzu4Vdb" +
            "GwU7+oKIJ7F9i2PMwZqWBB2LQQh9RarWwqcXnbTL066UvJH3yPRBhCMxTbZKU5/bygy+ggWC0TchsFJrmbHoB8zDnYXoGwg9AGf1fDINo3I4YMqQDMrOCU2R" +
            "xsNHRMGOeU1LA4l4huGeMxju25YI8V9mnHa3kKMkfCvczeM7okwXC/EQ+tRgqFiGIqU1XT7+S5+2GLk4Yte6H3th6+VACFCA2IswGvVA6NnE3EUoZPU+AdeZ" +
            "SiZXUyUyLZGT0staKlJrQnRTajQZjy/Gwya788rhc2uRqF0x7Qs70IKI0CxggQgst8c+sx/RbweG0tRCVZYaERRy4Qc7OTk/V4AIuJTtDRaCIRju2lqJR0EM" +
            "LL9ylOTXeRy82D3rO1ixe9Z3MqJSlQ0Ju3jc4PAGC/JS+Nstim1F5aoEo6dOqdSoLpYqFLIzKPOPLD6AIzu62yWbOfiwlKj1jqF0R0lkhbkaLB5BGx4SHLZY" +
            "/DF/CMCjCCW4N703cGu2kUexXYt5rSmYi1E6jwSGt1vNknRUBojj1n/Wyopt3QpofNjqdsRJO6PYxsaC0PPpeoOsHP0KEy7vGtUaMGlrFswjTCIa428IzB8C" +
            "d1haAUp3afmVM2iwNua+IasmLWLQPTPzeCvKQpHqE+HzPM7YuGDlwHDyEwNpCU0JfW3bhPgCE0wI/C6PeaYN53td6CMTNFjimL6gjVbm+WP+15wyJs7RIpik" +
            "fAwi/k+3l6M/spSD9Q6zCDwgltJEKgRnIPcRvoXQnfxRwShZ51Oax04l5hHZyXtPL66brz/ILf+Er65jQhjk74gv7AWnZZqwtmFjuJfRfRQMPGpvmnEqM9fe" +
            "oEGevEIJeBThcnmw5Smsql4sYlLFWWWHVoWjM8XxgQp94fY1lK9zAlQGMY+jlraEC1LDcv2Rc7FENY9ztUqa0zBEBJWVky7STsq+A1kexHULVwL8Ey7qvuTh" +
            "4ZKv4VnvPUbCgsGtn9p7l05Ss3OlrlGKoJaLgUe1lJVvVUPW4/wNvc7fLBMH5oa81ayoe5N7Ew/JT5QrSZJyIo0ITzJYxG08hQaGqCAW+mRRdHaTgJvkpcbn" +
            "tzx6X7yi128YJnzeafc/Wjqnq+HTMsbSPRv0K6b9NZJpPWXi9CuqzMrAN5jUhrTqBwmSM5ialS+uCFL3zChiqUahZJioya6QrDVPZEYyBpZEmg0VU+n4AQlD" +
            "LI6fsTAe699/MBq0zzSYL8hctAnVPntgiqL2dQLzjfwbAsZDddPfeHS8kW+Oobhjbo0Mh/tthj0krBJi2biRl/NcLSFNNnjratmjt2fsfql6nUk+TlCK4oxA" +
            "Ryfu8uZ8Axg+cGfTlsNzt/+iZxWGzdTFK8SbrC3ODEJInNHT+wNKwIqSyOMaXTH44qstkLUQN4nN6PI4vvp8Y67NjZtlZBLBfLRMQ3jQO5IJjhesH+jyaKOH" +
            "g82r4lx4tR+WXYOlY9PURVBn09V6et44m/ck9+sSdKZ/ZvDsFT0fmuyyiwd/2P7dFjcD14FxEajkAvu2wgkmeLy/K63fBnOSYvAjzlgtccyIUg8/z6c+QHGa" +
            "6O7qj7mUpwTFyU/OsKAsP3PCy9rBkbW5Wm7ZFH2VHpOhDYdRXa2OVCn9xeRUdaSqw19MmrBKhJ6z7U8NpD5xqN+6ldWxLOUiinNcxwYjJTDZZnCLWmKJ24Kc" +
            "CFUlW4LTRjmjP7UbAz9aL4Ugz5jjyqy4hT4jIiAhohJbWV1na2OO87QyJuJ0cHKkJFTles9BsDRUVaFqQR6IAlefbzyMX+a3j1cnesIBa6R96R9ZfIhkoPSn" +
            "aRMEhafQ0q5d0WjGcLRFQGTWniDZ+4MN8ksoetKte9YzpFeEuss6RmdAWXQgETDci5pwJ+iW0r1MswMj9GM7F9K7E55XkPiduyJ3LzHuw6gMBX9O0gJlk8mb" +
            "avMCwrt1eKiyONjpohZEy/xI46zHBspKifZqaNTncMx8oJ0IehAdWhi7Kua4mBLndqUVyKT0jqSCN4igUgRGPtJohuHelSCJcYJCBjf8khZXlX51t/NEV/Iu" +
            "2uBmIefcbBP3iMBWTytBNs/N61M396wxl5UuVQwT2GJlbTFSYSaKkMmvtXzoErWxJ27V9/FGQBuQnXg4riYaRg9gkR0IDn2lTiK0I9ISICB5pb91/1oAlHYt" +
            "ESKm1ajiWF7M5AiS9vcmr7b/jr3xqUH/3ogZnKR77tkccTR7o+1AhrNd+yIbTk/GEyDtj72TkUUTub+kFJ3RbcZSymozM+ejJl02YA+Yccr1K6E/Wmf5dqha" +
            "d2tKfTlWb/ytGSOJ6LZjHS58+x28ymSGsCycVTzH5u9WBP5dl/k8KcHfWbMsTvRnWe7XsiArVrP9N3CXEY4PBCsPvXc2tq4UJjjdiRukv/yHPDMmru8RPGG+" +
            "A4oBnDffObhPGtwGbrI66uNaLflKC1GaG8PctGz+lZEsamkAZVYcP5vXfD4vvOFeT1zXEwV+d7i3iNQ6JfkRSWMTBOXLed2c07x7KkYqynshJpWpFN9eNr3o" +
            "h8AXByvIrdFXGZH4cjNbGgkcsQbH0vy59Cvs9lthnd1ZnxDNAcvgwRFFrDyd4nhqlhzn07Hqt9R8iPVAfINLRmjscOInGugVF2pEho+nCMKUSxPcfCx5xhFr" +
            "dEdNxZdNXWhRjFFdhSJs7XxlhqLKG9KQEeVgW+RuE5+l051j2bJ2XTRU71RKWuXjp9/WWjGllqQ3Hv1u1wGo1V4GYOmM0lejs1fV2KB8vjCiTGUIY75D0OKN" +
            "FdyjW83Gt+LHd5ZhfWQ+aRTZLcKkEfq2yhiqUrydKPVcpkScTb+rnIibH66Nz9HJMv/U4y8dJbLm6tJR8GtA86Oiq4NNRc5JxC4Ouaut1TzqClh9Ok/Y5PVf" +
            "GS2AWC8Hdn1oJQdOn1PKDr4K42OrQW0+GVmfH5EhsOJzgSL8scrU3f9q/X25wr7Aq0n5iPeHYK03QvFB53qCesRPfo9u2FsOhouLhsft+74GNHB/1rk2Xgdj" +
            "dGBUXEJp6WiXH2n8PhPQl8H80SvX8t87AAvIZSxzMP7frNsdRNfL27vl+uPgXP2cXy+D9cPtdL38OJioR7fLq6ubT1fBx8GFejC7v18HH7vqR/B0/7j42M9b" +
            "bu/nv38cFk3r6c1j8HHYy3/eTmcfhzlydXv/tHz8OBzkbdNPi4+9Ygz3dw/TIDAiFwy+1hbnO6ry32V2uxLD7X91ypVkbr0Xd8K69W7yfPVPXC1i5fdP9DAa" +
            "nnJcd0hdAQz2CJn1b410Y61GNJlM/JMjH/76fzn2qOQfZQAA";

    private DefaultModuleProfile() { }

    static JsonObject read() {
        try {
            byte[] compressed = Base64.getDecoder().decode(DATA);
            Reader reader = new InputStreamReader(new GZIPInputStream(new ByteArrayInputStream(compressed)),
                    StandardCharsets.UTF_8);
            try {
                JsonElement value = new JsonParser().parse(reader);
                JsonObject profile = value != null && value.isJsonObject() ? value.getAsJsonObject() : new JsonObject();
                addNewGuiTargets(profile);
                return profile;
            } finally {
                reader.close();
            }
        } catch (Exception ignored) {
            return new JsonObject();
        }
    }

    /** Keep newly introduced GUI surfaces usable with the supplied preset. */
    private static void addNewGuiTargets(JsonObject profile) {
        if (!profile.has("modules") || !profile.get("modules").isJsonArray()) return;
        for (JsonElement item : profile.getAsJsonArray("modules")) {
            if (!item.isJsonObject()) continue;
            JsonObject module = item.getAsJsonObject();
            if (!module.has("settings") || !module.get("settings").isJsonObject()) continue;
            JsonObject settings = module.getAsJsonObject("settings");
            String id = module.has("id") ? module.get("id").getAsString() : "";
            if ("blur".equalsIgnoreCase(id)) add(settings, "Blur Elements", "memegames");
            else if ("particles".equalsIgnoreCase(id)) add(settings, "Show In", "Meme Games");
            else if ("waifu".equalsIgnoreCase(id)) add(settings, "Show In", "Meme Games");
        }
    }

    private static void add(JsonObject settings, String key, String value) {
        JsonArray selected = settings.has(key) && settings.get(key).isJsonArray()
                ? settings.getAsJsonArray(key) : new JsonArray();
        for (JsonElement entry : selected) if (value.equalsIgnoreCase(entry.getAsString())) return;
        selected.add(new JsonPrimitive(value)); settings.add(key, selected);
    }
}
