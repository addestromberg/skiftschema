package se.skiftschema.app;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 29)
public class PhoneCalendarIdentityTest {
    private static final String OWNER = "se.skiftschema.app";
    private static final String LOCAL = CalendarContract.ACCOUNT_TYPE_LOCAL;

    @Test public void reusesRenamedExpoCalendarWithOrWithoutSavedId() throws Exception {
        try (CalendarProviderFixture provider = fixture()) {
            long expoId = add(provider, OWNER, OWNER, LOCAL, OWNER, "Mitt skift");
            PhoneCalendar calendar = new PhoneCalendar(ContentResolver.wrap(provider));
            assertEquals(Long.valueOf(expoId), calendar.open(expoId, true));
            assertEquals(Long.valueOf(expoId), calendar.open(null, false));
            assertEquals(Long.valueOf(expoId), calendar.open(expoId + 100, true));
            assertEquals(1, provider.database.compileStatement("SELECT COUNT(*) FROM calendars").simpleQueryForLong());
        }
    }

    @Test public void ignoresForeignSavedIdsEvenWithMatchingDisplayName() throws Exception {
        try (CalendarProviderFixture provider = fixture()) {
            long wrongName = add(provider, "personal", OWNER, LOCAL, OWNER, "Skiftschema");
            long wrongAccount = add(provider, OWNER, "personal", LOCAL, OWNER, "Skiftschema");
            long wrongType = add(provider, OWNER, OWNER, "com.google", OWNER, "Skiftschema");
            long wrongOwner = add(provider, OWNER, OWNER, LOCAL, "personal", "Skiftschema");
            PhoneCalendar calendar = new PhoneCalendar(ContentResolver.wrap(provider));
            assertNull(calendar.open(wrongName, false));
            assertNull(calendar.open(wrongAccount, false));
            assertNull(calendar.open(wrongType, false));
            assertNull(calendar.open(wrongOwner, false));
            Long owned = calendar.open(wrongOwner, true);
            assertNotEquals(Long.valueOf(wrongOwner), owned);
            assertEquals(owned, calendar.open(wrongAccount, false));
            long second = add(provider, OWNER, OWNER, LOCAL, OWNER, "Arbetspass");
            assertEquals(Long.valueOf(second), calendar.open(second, false));
        }
    }

    private CalendarProviderFixture fixture() {
        return new CalendarProviderFixture(InstrumentationRegistry.getInstrumentation().getTargetContext());
    }

    private long add(CalendarProviderFixture provider, String name, String account,
                      String accountType, String owner, String title) {
        ContentValues values = new ContentValues();
        values.put(Calendars.NAME, name);
        values.put(Calendars.ACCOUNT_NAME, account);
        values.put(Calendars.ACCOUNT_TYPE, accountType);
        values.put(Calendars.OWNER_ACCOUNT, owner);
        values.put(Calendars.CALENDAR_DISPLAY_NAME, title);
        return provider.database.insertOrThrow("calendars", null, values);
    }
}
