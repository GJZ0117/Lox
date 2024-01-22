package cn.gjz.lox;

/**
 * 使用Java运行时异常类来封装返回值
 */

public class Return extends RuntimeException {
    final Object value;

    Return(Object value) {
        super(null, null, false, false);
        this.value = value;
    }
}
