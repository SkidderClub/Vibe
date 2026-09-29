package dev.vibe.account;

import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class AutoLoginPreferenceTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void exactlyOneAccountSurvivesRestartAndCredentialRefresh() throws Exception {
        Path directory = temporary.newFolder().toPath();
        Account first = Account.offline("First"), second = new Account("Second", java.util.UUID.randomUUID(), "synthetic-refresh");
        AutoLoginPreference preference = new AutoLoginPreference(directory);
        assertFalse(preference.matches(first));
        preference.set(first);
        assertTrue(new AutoLoginPreference(directory).matches(first));
        preference.set(second);
        preference = new AutoLoginPreference(directory);
        assertFalse(preference.matches(first));
        assertTrue(preference.matches(new Account("Renamed", second.getUuid(), "rotated-refresh")));
        assertFalse(preference.matches(new Account("Second", second.getUuid(), "")));
        preference.set(null);
        assertFalse(new AutoLoginPreference(directory).matches(second));
    }

    @Test public void accessTokenAccountIsDistinctFromMicrosoftAccountOfTheSameProfile() throws Exception {
        java.util.UUID id = java.util.UUID.randomUUID();
        Account pasted = Account.token("Example", id, MinecraftTokenTest.jwt("{}"));
        AutoLoginPreference preference = new AutoLoginPreference(temporary.newFolder().toPath());
        preference.set(pasted);
        assertTrue(preference.matches(Account.token("Renamed", id, MinecraftTokenTest.jwt("{\"exp\":1}"))));
        assertFalse(preference.matches(new Account("Example", id, "synthetic-refresh")));
        assertFalse(preference.matches(new Account("Example", id, "")));
    }
}
