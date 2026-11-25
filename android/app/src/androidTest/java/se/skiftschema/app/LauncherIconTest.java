package se.skiftschema.app;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;

import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class LauncherIconTest {
    @Test @SdkSuppress(maxSdkVersion = 25)
    public void legacyLauncherIconHasTransparentMargins() {
        Bitmap icon = render(launcherIcon());
        assertEquals(Color.TRANSPARENT, icon.getPixel(4, 64));
        assertEquals(Color.TRANSPARENT, icon.getPixel(64, 4));
        assertEquals(Color.TRANSPARENT, icon.getPixel(123, 64));
        assertEquals(Color.TRANSPARENT, icon.getPixel(64, 123));
        assertEquals(255, Color.alpha(icon.getPixel(20, 64)));
    }

    @Test @SdkSuppress(minSdkVersion = 33)
    public void themedLauncherCanTintTheCalendarGlyphWithoutColoringItsBackground() {
        Drawable icon = launcherIcon();
        assertTrue(icon instanceof AdaptiveIconDrawable);
        Drawable monochrome = ((AdaptiveIconDrawable) icon).getMonochrome();
        assertNotNull(monochrome);
        monochrome = monochrome.mutate();
        monochrome.setTint(Color.MAGENTA);
        Bitmap image = render(monochrome);
        assertEquals(Color.TRANSPARENT, image.getPixel(0, 0));
        int glyphPixels = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getPixel(x, y) == Color.MAGENTA) glyphPixels++;
            }
        }
        assertTrue(glyphPixels > 0);
    }

    private Drawable launcherIcon() {
        var context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        return context.getPackageManager().getApplicationIcon(context.getApplicationInfo());
    }

    private Bitmap render(Drawable drawable) {
        Bitmap bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
        drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
        drawable.draw(new Canvas(bitmap));
        return bitmap;
    }
}
