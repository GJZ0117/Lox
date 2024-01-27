package cn.gjz.lox;

/**
 * 将token有关的信息打包为一个类
 */

public class Token {
    final TokenType type; // token的类型
    final String lexeme; // 字符表现形式
    final Object literal; // 变量的实际值
    final int line; // token所在行号

    public Token(TokenType type, String lexeme, Object literal, int line) {
        this.type = type;
        this.lexeme = lexeme;
        this.literal = literal;
        this.line = line;
    }

    @Override
    public String toString() {
        return "Token{" +
                "type=" + type +
                ", lexeme='" + lexeme + '\'' +
                ", literal=" + literal +
                '}';
    }

    // 书中toString写法
    // public String toString() {
    //     return type + " " + lexeme + " " + literal;
    // }
}
