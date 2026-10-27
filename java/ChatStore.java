package io.lhagnet.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;

/**
 * 聊天记录：一键一行 JSON 存文件，重启不丢。
 * 角色：user（我说的）/ agent（它的结论与进度）/ system（提示与错误）
 */
public class ChatStore {

    public static class Msg {
        public String role;   // user / agent / system
        public String text;
        public long ts;
    }

    private final List<Msg> messages = new ArrayList<Msg>();
    private final File file;

    public ChatStore(Context ctx) {
        File dir = new File(ctx.getFilesDir(), "chat");
        if (!dir.exists()) dir.mkdirs();
        this.file = new File(dir, "chat.json");
        load();
    }

    public synchronized void load() {
        messages.clear();
        String text = readAll();
        if (text.length() == 0) return;
        try {
            JSONArray arr = new JSONArray(text);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Msg m = new Msg();
                m.role = o.optString("role", "agent");
                m.text = o.optString("text", "");
                m.ts = o.optLong("ts", 0);
                messages.add(m);
            }
        } catch (Throwable ignored) {
        }
    }

    public synchronized void add(String role, String text) {
        if (text == null) text = "";
        load(); // 先同步磁盘：MainActivity 与 AgentService 各有一个实例，不重读会互相覆盖
        Msg m = new Msg();
        m.role = role;
        m.text = text;
        m.ts = System.currentTimeMillis();
        messages.add(m);
        while (messages.size() > 300) messages.remove(0);
        save();
    }

    public synchronized void appendToLast(String extra) {
        load();
        if (messages.isEmpty()) {
            add("agent", extra);
            return;
        }
        Msg last = messages.get(messages.size() - 1);
        if (last.text.length() > 4000) {
            last.text = last.text.substring(last.text.length() - 3000);
        }
        last.text = last.text + "\n" + extra;
        save();
    }

    /** 按内容删除一条（界面上的下标可能因为并发写入而错位，用内容匹配更稳） */
    public synchronized boolean remove(String role, String text, long ts) {
        load();
        for (int i = messages.size() - 1; i >= 0; i--) {
            Msg m = messages.get(i);
            if (m.ts == ts && safeEq(m.role, role) && safeEq(m.text, text)) {
                messages.remove(i);
                save();
                return true;
            }
        }
        return false;
    }

    private static boolean safeEq(String a, String b) {
        if (a == null) return b == null;
        return a.equals(b);
    }
    public synchronized void clear() {
        messages.clear();
        save();
    }

    public synchronized int size() {
        return messages.size();
    }

    public synchronized Msg get(int i) {
        return messages.get(i);
    }

    public synchronized List<Msg> all() {
        return new ArrayList<Msg>(messages);
    }

    /** 最近几轮对话，用于给模型当上下文 */
    public synchronized String recentContext(int maxChars) {
        StringBuilder sb = new StringBuilder();
        int from = Math.max(0, messages.size() - 10);
        for (int i = from; i < messages.size(); i++) {
            Msg m = messages.get(i);
            if (m.role == null || m.text == null) continue;
            if ("system".equals(m.role)) continue;
            sb.append("user".equals(m.role) ? "用户：" : "助手：").append(oneLine(m.text)).append("\n");
        }
        String s = sb.toString();
        return s.length() > maxChars ? s.substring(s.length() - maxChars) : s;
    }

    private static String oneLine(String s) {
        String t = s.replace("\n", " ");
        return t.length() > 160 ? t.substring(0, 160) + "…" : t;
    }

    private void save() {
        try {
            JSONArray arr = new JSONArray();
            for (Msg m : messages) {
                JSONObject o = new JSONObject();
                o.put("role", m.role);
                o.put("text", m.text);
                o.put("ts", m.ts);
                arr.put(o);
            }
            FileOutputStream fos = new FileOutputStream(file, false);
            fos.write(arr.toString().getBytes("UTF-8"));
            fos.flush();
            fos.close();
        } catch (Throwable ignored) {
        }
    }

    private String readAll() {
        try {
            if (!file.exists()) return "";
            RandomAccessFile raf = new RandomAccessFile(file, "r");
            byte[] buf = new byte[(int) raf.length()];
            raf.readFully(buf);
            raf.close();
            return new String(buf, "UTF-8");
        } catch (Throwable t) {
            return "";
        }
    }
}
