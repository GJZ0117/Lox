package cn.gjz.lox;

import java.util.HashMap;
import java.util.Map;

/**
 * Lox类实例的运行时表示
 */

public class LoxInstance {
    private LoxClass klass;

    // map中的每个键是一个属性名称，对应的值就是该属性的值
    private final Map<String, Object> fields = new HashMap<>();

    public LoxInstance(LoxClass klass) {
        this.klass = klass;
    }

    // 查找实例中的一个属性
    Object get(Token name) {
        if (fields.containsKey(name.lexeme)) {
            return fields.get(name.lexeme);
        }
        // 在实例上查找属性时，如果没有找到匹配的字段，就在实例的类中查找是否包含该名称的方法
        // 当访问一个属性时，可能会得到一个字段（存储在实例上的状态值），或者会得到一个实例类中定义的方法
        LoxFunction method = klass.findMethod(name.lexeme);
        if (method != null) {
            return method.bind(this);
        }
        throw new RuntimeError(name, " Undefined property '" + name.lexeme + "'.");
    }

    void set(Token name, Object value) {
        fields.put(name.lexeme, value);
    }

    @Override
    public String toString() {
        return klass.name + " instance";
    }
}
