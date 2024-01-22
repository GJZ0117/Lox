package cn.gjz.lox;

import java.util.List;

/**
 * 任何可以像函数一样被调用的Lox对象的Java表示都要实现这个接口
 */

interface LoxCallable {
    // 查询函数参数个数
    int arity();
    // 将被调用者转换为LoxCallable，然后对其调用call()方法来实现
    Object call(Interpreter interpreter, List<Object> arguments);
}
