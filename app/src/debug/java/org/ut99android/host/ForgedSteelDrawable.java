package org.ut99android.host;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import java.util.Random;

/** Original, procedurally generated steel texture; no game artwork is bundled. */
final class ForgedSteelDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final BitmapShader texture;
    ForgedSteelDrawable() {
        Bitmap tile = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(tile); c.drawColor(Color.rgb(26, 30, 33));
        Paint grain = new Paint(); Random random = new Random(991997);
        for (int i = 0; i < 4200; i++) {
            int light = random.nextInt(35) + 18;
            grain.setColor(Color.argb(random.nextInt(65) + 20, light + 25, light + 22, light + 18));
            int x = random.nextInt(256), y = random.nextInt(256);
            c.drawLine(x, y, Math.min(255, x + random.nextInt(16) + 1), y, grain);
        }
        grain.setStrokeWidth(1);
        for (int i = 0; i < 45; i++) {
            int x = random.nextInt(256), y = random.nextInt(256), length = 8 + random.nextInt(55);
            grain.setColor(Color.argb(35, 160, 153, 140));
            c.drawLine(x, y, Math.min(255, x + length), Math.max(0, y - 1), grain);
            grain.setColor(Color.argb(45, 0, 0, 0));
            c.drawLine(x, y + 1, Math.min(255, x + length), y, grain);
        }
        texture = new BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
    }
    @Override public void draw(Canvas canvas) {
        android.graphics.Rect b = getBounds();
        paint.setShader(texture); canvas.drawRect(b, paint);
        paint.setShader(new LinearGradient(b.left, b.top, b.right, b.bottom,
                new int[]{0xaa080b0d, 0x222f3335, 0xcc08090b}, null, Shader.TileMode.CLAMP));
        canvas.drawRect(b, paint); paint.setShader(null);
        paint.setColor(0x554f5758); paint.setStrokeWidth(2);
        canvas.drawLine(b.left + 8, b.top, b.left + 8, b.bottom, paint);
        canvas.drawLine(b.right - 8, b.top, b.right - 8, b.bottom, paint);
    }
    @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); invalidateSelf(); }
    @Override public void setColorFilter(ColorFilter filter) { paint.setColorFilter(filter); invalidateSelf(); }
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
