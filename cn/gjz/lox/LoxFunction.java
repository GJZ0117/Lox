package cn.gjz.lox;

import java.util.List;

public class LoxFunction implements LoxCallable {

    private final Stmt.Function declaration;

    // 存储环境
    private final Environment closure;

    private final boolean isInitializer;

    LoxFunction(Stmt.Function declaration, Environment closure, boolean isInitializer) {
        this.closure = closure;
        this.declaration = declaration;
        this.isInitializer = isInitializer;
    }

    // 基于方法的原始闭包创建了一个新的环境，就像是闭包内的闭包
    // 当方法被调用时，它将变成方法体对应环境的父环境
    LoxFunction bind(LoxInstance instance) {
        // 将this声明为该环境中的一个变量，并将其绑定到给定的实例
        Environment environment = new Environment(closure);
        environment.define("this", instance);
        // 在创建闭包并将this绑定到新方法时，将原始方法的值传递给新方法
        return new LoxFunction(declaration, environment, isInitializer);
    }

    // 返回函数参数个数
    @Override
    public int arity() {
        return declaration.params.size();
    }

    // 遍历几个列表，绑定一些新变量，调用一个方法，将代码块变成有生命力的调用执行的地方
    // call()会告诉解释器在这个新的函数局部环境中执行函数体
    @Override
    public Object call(Interpreter interpreter, List<Object> arguments) {
        // 动态创建函数的环境 每次函数调用都会获得自己的环境
        // 创建了一个环境链，从函数体开始，经过函数被声明的环境，然后到全局作用域
        Environment environment = new Environment(closure);
        // 以同步的方式遍历形参和实参列表，将每个函数中的变量存储在自己的环境中
        for (int i = 0; i < declaration.params.size(); i++) {
            // 对每一对参数，用形参的名字创建一个新的变量，并将其与实参的值绑定
            environment.define(declaration.params.get(i).lexeme, arguments.get(i));
        }
        // 通过在执行函数主体时使用不同的环境，用同样的代码调用相同的函数可以产生不同的结果
        try {
            // 如果没有捕获任何异常，意味着函数到达了函数体的末尾，而且没有遇到return语句，在这种情况下，隐式地返回nil
            interpreter.executeBlock(declaration.body, environment);
        } catch (Return returnValue) {
            // 构造方法的return返回this
            if (isInitializer) {
                return closure.getAt(0, "this");
            }
            return returnValue.value;
        }
        // 如果该函数是一个构造方法，覆盖实际的返回值并强行返回this
        if (isInitializer) {
            return closure.getAt(0, "this");
        }
        return null;
    }

    @Override
    public String toString() {
        return "<fn " + declaration.name.lexeme + ">";
    }
}
