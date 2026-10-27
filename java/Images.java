package io.lhagnet.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;

/**
 * 图片处理：把用户发来的截图/照片压成模型能吃的尺寸，再转 base64。
 *
 * 手机拍的原图动辄 4~8MB，直接塞进请求会超时/超限；
 * 这里统一缩到最长边 1024、JPEG 75，一般 80~200KB，够模型看清文字。
 */
public class Images {

    public static byte[] toJpegBytes(File f, int maxSide, int quality) {
        try {
            if (f == null || !f.exists() || f.length() == 0) return null;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
            int w = opts.outWidth;
            int h = opts.outHeight;
            if (w <= 0 || h <= 0) return null;
            int sample = 1;
            while (Math.max(w / sample, h / sample) > maxSide * 2) sample *= 2;
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath(), opts);
            if (bmp == null) return null;
            int cw = bmp.getWidth();
            int ch = bmp.getHeight();
            int longSide = Math.max(cw, ch);
            if (longSide > maxSide) {
                float k = (float) maxSide / (float) longSide;
                Bitmap scaled = Bitmap.createScaledBitmap(bmp, Math.max(1, Math.round(cw * k)),
                        Math.max(1, Math.round(ch * k)), true);
                if (scaled != bmp) bmp.recycle();
                bmp = scaled;
            }
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, bos);
            bmp.recycle();
            return bos.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }

    /** 给模型的 data URI（data:image/jpeg;base64,...） */
    public static String dataUri(File f, int maxSide, int quality) {
        byte[] bytes = toJpegBytes(f, maxSide, quality);
        if (bytes == null) return "";
        return "data:image/jpeg;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP);
    }
}
