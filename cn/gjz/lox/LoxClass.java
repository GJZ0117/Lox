package cn.gjz.lox;

import java.util.List;
import java.util.Map;

/**
 * Lox类
 */

public class LoxClass implements LoxCallable {

    final String name;
    final LoxClass superClass;
    // 包含方法的map
    private final Map<String, LoxFunction> methods;

    LoxClass(String name, LoxClass superClass, Map<String, LoxFunction> methods) {
        this.name = name;
        this.superClass = superClass;
        this.methods = methods;
    }

    // 查找类中的方法
    LoxFunction findMethod(String name) {
        // 首先在当前类中查找，然后遍历父类链；如果在子类和父类中包含相同的方法，那么子类中的方法将优先于或覆盖父类的方法
        if (methods.containsKey(name)) {
            return methods.get(name);
        }
        // 在子类实例上查找父类的方法
        if (superClass != null) {
            return superClass.findMethod(name);
        }
        return null;
    }

    @Override
    public String toString() {
        return name;
    }

    // 当“调用”一个类时，它会为被调用的类实例化一个新的LoxInstance并返回
    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        LoxInstance instance = new LoxInstance(this);
        // 用户自定义的构造方法，为类建立新对象
        LoxFunction initializer = findMethod("init");
        if (initializer != null) {
            initializer.bind(instance).call(interpreter, arguments);
        }
        return instance;
    }

    @Override
    public int arity() {
        // 类构造方法init()的参数列表
        LoxFunction initializer = findMethod("init");
        // 没有构造方法元数仍然是0
        if (initializer == null) {
            return 0;
        }
        // 如果有构造方法，该方法的元数就决定了在调用类本身的时候需要传入多少个参数
        return initializer.arity();
    }
}
