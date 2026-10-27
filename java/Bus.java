package io.lhagnet.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 进程内的极简事件总线：AgentService（后台线程）→ MainActivity（UI）。
 * 同时把事件写进 logcat 和应用私有目录下的 logs/lhagnet.log。
 *
 * 【安全】日志放在 getFilesDir()（应用私有目录）而不是 getExternalFilesDir()。
 * 外部存储上的文件任何有存储权限的 App 都能读，而这份日志里会包含
 * 手机号、任务目标、模型返回内容等敏感信息，属于个人信息。
 * 私有目录只有本应用（以及 root / 备份工具）能读。
 */
public class Bus {
    public interface Listener {
        void onEvent(String type, String message);
    }

    private static final List<Listener> listeners = new ArrayList<Listener>();
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final List<String> history = new ArrayList<String>();
    private static final int MAX_HISTORY = 400;
    private static final long MAX_LOG_BYTES = 512 * 1024;
    private static File logFile;
    private static final Object fileLock = new Object();
    private static final SimpleDateFormat TS = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    /** 在 Application/Activity/Service 启动时调一次，建立日志文件 */
    public static void init(Context ctx) {
        try {
            // 只用应用私有目录：日志含手机号 / 任务内容，不放外部存储
            File dir = new File(ctx.getFilesDir(), "logs");
            if (!dir.exists()) dir.mkdirs();
            logFile = new File(dir, "lhagnet.log");
        } catch (Throwable ignored) {
        }
    }

    public static File logFile() {
        return logFile;
    }

    /** 详细 trace（每步完整观测），只落盘不进聊天，排障用 */
    public static void trace(String text) {
        synchronized (fileLock) {
            if (logFile == null) return;
            try {
                File f = new File(logFile.getParentFile(), "trace.log");
                if (f.length() > 2 * 1024 * 1024) {
                    FileOutputStream trunc = new FileOutputStream(f, false);
                    trunc.close();
                }
                FileOutputStream fos = new FileOutputStream(f, true);
                fos.write((TS.format(new Date()) + " " + (text == null ? "" : text) + "\n---\n").getBytes("UTF-8"));
                fos.flush();
                fos.close();
            } catch (Throwable ignored) {
            }
        }
    }

    public static void add(Listener l) {
        synchronized (listeners) {
            if (!listeners.contains(l)) listeners.add(l);
        }
    }

    public static void remove(Listener l) {
        synchronized (listeners) {
            listeners.remove(l);
        }
    }

    /** 线程安全地广播一条事件；type 供 UI 决定前缀样式 */
    public static void emit(final String type, final String message) {
        synchronized (history) {
            history.add(type + "\u0001" + message);
            while (history.size() > MAX_HISTORY) history.remove(0);
        }
        // 同时打到 logcat，方便用 adb logcat 远程排障
        try {
            Log.i("LhAgnet", type + ": " + message);
        } catch (Throwable ignored) {
        }
        main.post(new Runnable() {
            public void run() {
                List<Listener> copy;
                synchronized (listeners) {
                    copy = new ArrayList<Listener>(listeners);
                }
                for (Listener l : copy) {
                    try {
                        l.onEvent(type, message);
                    } catch (Throwable ignored) {
                    }
                }
            }
        });
        writeFile(type, message);
    }

    private static void writeFile(String type, String message) {
        synchronized (fileLock) {
            if (logFile == null) return;
            try {
                String line = TS.format(new Date()) + " [" + type + "] " + message + "\n";
                if (logFile.length() > MAX_LOG_BYTES) {
                    // 超过 512KB 就只保留最后一半，避免无限增长
                    RandomAccessFile raf = new RandomAccessFile(logFile, "rw");
                    long keep = MAX_LOG_BYTES / 2;
                    long len = raf.length();
                    byte[] tail = new byte[(int) Math.min(keep, len)];
                    raf.seek(len - tail.length);
                    raf.readFully(tail);
                    raf.setLength(0);
                    raf.write(tail);
                    raf.close();
                }
                FileOutputStream fos = new FileOutputStream(logFile, true);
                fos.write(line.getBytes("UTF-8"));
                fos.flush();
                fos.close();
            } catch (Throwable ignored) {
            }
        }
    }

    public static List<String> snapshot() {
        synchronized (history) {
            return new ArrayList<String>(history);
        }
    }

    public static void clear() {
        synchronized (history) {
            history.clear();
        }
    }
}
