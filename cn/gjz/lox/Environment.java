package cn.gjz.lox;

import java.util.HashMap;
import java.util.Map;

/**
 * 变量与值之间的绑定关系，键是变量名称，值就是变量的值
 */

public class Environment {

    // 该引用指的是上一层environment
    final Environment enclosing;

    private final Map<String, Object> values = new HashMap<>();

    Environment() {
        enclosing = null;
    }

    Environment(Environment enclosing) {
        this.enclosing = enclosing;
    }

    // 返回与变量名称绑定的变量
    public Object get(Token name) {
        if (values.containsKey(name.lexeme)) {
            return values.get(name.lexeme);
        }
        // 如果当前环境中没有找到变量，就在外围环境中尝试,递归地重复该操作，最终会遍历完整个链路
        if (enclosing != null) {
            return enclosing.get(name);
        }
        throw new RuntimeError(name, "Undefined variable '" + name.lexeme + "'.");
    }

    // 赋值操作不允许创建新变量
    void assign(Token name, Object value) {
        if (values.containsKey(name.lexeme)) {
            values.put(name.lexeme, value);
            return;
        }
        // 如果变量不在此环境中，它会递归地检查外围环境
        if (enclosing != null) {
            enclosing.assign(name, value);
            return;
        }
        throw new RuntimeError(name, "Undefined variable '" + name.lexeme + "'.");
    }

    // 变量定义操作
    public void define(String name, Object value) {
        values.put(name, value);
    }

    // getAt()对应get() 返回对应环境map中的变量值
    Object getAt(int distance, String name) {
        return ancestor(distance).values.get(name);
    }

    // assignAt()对应assign() 遍历固定数量的环境，然后在其map中塞入新的值
    void assignAt(int distance, Token name, Object value) {
        ancestor(distance).values.put(name.lexeme, value);
    }

    // 直达链路中包含该变量的环境
    Environment ancestor(int distance) {
        Environment environment = this;
        for (int i = 0; i < distance; i++) {
            environment = environment.enclosing;
        }

        return environment;
    }
}
