package cn.gjz.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.gjz.lox.TokenType.*;

/**
 * 扫描器
 */

public class Scanner {
    private final String source;
    private final List<Token> tokens = new ArrayList<>();

    // 在map中定义保留字
    private static final Map<String, TokenType> keywords;

    static {
        keywords = new HashMap<>();
        keywords.put("and", AND);
        keywords.put("class", CLASS);
        keywords.put("else", ELSE);
        keywords.put("false", FALSE);
        keywords.put("for", FOR);
        keywords.put("fun", FUN);
        keywords.put("if", IF);
        keywords.put("nil", NIL);
        keywords.put("or", OR);
        keywords.put("print", PRINT);
        keywords.put("return", RETURN);
        keywords.put("super", SUPER);
        keywords.put("this", THIS);
        keywords.put("true", TRUE);
        keywords.put("var", VAR);
        keywords.put("while", WHILE);
    }

    // 跟踪扫描器在源代码中的位置
    private int start = 0; // 指向被扫描的词第一个字符
    private int current = 0; // 当前正在处理的字符
    private int line = 0; // current所在源文件的行数

    public Scanner(String source) {
        this.source = source;
    }

    // 用一个列表来保存扫描时产生的标记
    public List<Token> scanTokens() {
        while (!isAtEnd()) {
            start = current;
            scanToken();
        }
        tokens.add(new Token(EOF, "", null, line));
        return tokens;
    }

    // 消费字符
    private void scanToken() {
        char c = advance();
        switch (c) {
            // 以下为单字符匹配
            case '(':
                addToken(LEFT_PAREN);
                break;
            case ')':
                addToken(RIGHT_PAREN);
                break;
            case '{':
                addToken(LEFT_BRACE);
                break;
            case '}':
                addToken(RIGHT_BRACE);
                break;
            case ',':
                addToken(COMMA);
                break;
            case '.':
                addToken(DOT);
                break;
            case '-':
                addToken(MINUS);
                break;
            case '+':
                addToken(PLUS);
                break;
            case ';':
                addToken(SEMICOLON);
                break;
            case '*':
                addToken(STAR);
                break;
            // 以下为双字符匹配
            case '!':
                addToken(match('=') ? BANG_EQUAL : BANG);
                break;
            case '=':
                addToken(match('=') ? EQUAL_EQUAL : EQUAL);
                break;
            case '<':
                addToken(match('=') ? LESS_EQUAL : LESS);
                break;
            case '>':
                addToken(match('=') ? GREATER_EQUAL : GREATER);
                break;
            // 除号/要特殊处理，因为注释开头也是/
            case '/':
                // 找到第二个/时会继续消费字符直到行尾
                if (match('/')) {
                    while (peek() != '\n' && !isAtEnd()) advance();
                } else {
                    addToken(SLASH);
                }
                break;
            // 跳过无意义的字符：换行和空格
            case ' ':
            case '\r':
            case '\t':
                break;
            case '\n':
                line++;
                break;
            // 处理字符串
            case '"':
                string();
                break;
            default:
                // 识别数字
                if (isDigit(c)) {
                    number();
                } else if (isAlpha(c)) {
                    // 假设以字母或下划线开头的均为标识符
                    identifier();
                } else {
                    // 违法的字符
                    Lox.error(line, "Unexpected character.");
                }
                break;
        }
    }

    // 扫描一个标识符片段
    private void identifier() {
        while (isAlphaNumeric(peek())) {
            advance();
        }
        // 扫描到标识符后，检查是否与map中的某些项匹配，如果匹配就使用关键字标及类型，否则就是一个用户定义的标识符
        String text = source.substring(start, current);
        TokenType type = keywords.get(text);
        if (type == null) {
            type = IDENTIFIER;
        }
        addToken(type);
    }

    // 处理当前扫描到的数字及其后面的数字字符
    private void number() {
        while (isDigit(peek())) {
            advance();
        }
        // 找到小数点
        if (peek() == '.' && isDigit(peekNext())) {
            // 消费小数点.
            advance();
            while (isDigit(peek())) {
                advance();
            }
        }
        addToken(NUMBER, Double.parseDouble(source.substring(start, current)));
    }

    // 一直消费字符直到遇到第二个"，如果内容耗尽则报错
    private void string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') {
                line++;
            }
            advance();
        }

        if (isAtEnd()) {
            Lox.error(line, "Unterminated string.");
            return;
        }
        // 第二个"
        advance();
        // 提取两个"之间的字符串
        String value = source.substring(start + 1, current - 1);
        addToken(STRING, value);
    }

    // 只有当前字符是正在寻找的字符时才会消费
    private boolean match(char expected) {
        if (isAtEnd()) {
            return false;
        }
        if (source.charAt(current) != expected) {
            return false;
        }
        current++;
        return true;
    }

    // 类似advance方法，只是不会消费字符，而是前瞻字符
    private char peek() {
        if (isAtEnd()) {
            return '\0';
        }
        return source.charAt(current);
    }

    // 找到小数点后继续前瞻下一个字符是否为数字
    private char peekNext() {
        if (current + 1 >= source.length()) {
            return '\0';
        }
        return source.charAt(current + 1);
    }

    // 判断是否是构成标识符的字符(大小写字母、下划线)
    private boolean isAlpha(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    // 判断是否是构成标识符的字符(大小写字母、数字、下划线)
    private boolean isAlphaNumeric(char c) {
        return isAlpha(c) || isDigit(c);
    }

    // 判断是否是数字
    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    // 获取源文件中的下一个字符并返回它（输入）
    private char advance() {
        current++;
        return source.charAt(current - 1);
    }

    // 获取当前词的文本并为其创建一个新的token（输出）
    private void addToken(TokenType type) {
        addToken(type, null);
    }

    // 同上面的重载方法，处理带有字面值的token
    private void addToken(TokenType type, Object literal) {
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line));
    }

    // 是否已消费完所有字符
    private boolean isAtEnd() {
        return current >= source.length();
    }
}
