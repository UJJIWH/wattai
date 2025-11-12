package stud.g01.solver.pattern;

import java.util.HashMap;
import java.util.Map;

/**
 * 模式数据库管理器
 * 负责管理和缓存模式数据库实例
 */
public class PatternDatabaseManager {
    private static final Map<String, PatternDatabase> databaseCache = new HashMap<>();

    public static PatternDatabase getDatabase(String type, int size) {
        String key = type + "_" + size;
        if (!databaseCache.containsKey(key)) {
            PatternDatabase db = PatternDatabaseBuilder.createByName(type, size);
            System.out.println("预计算模式数据库: " + type);
            db.precompute();
            databaseCache.put(key, db);
        }
        return databaseCache.get(key);
    }

    public static void clearCache() {
        databaseCache.clear();
    }

    public static int getCacheSize() {
        return databaseCache.size();
    }
}