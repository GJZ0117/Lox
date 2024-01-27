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

    // 在map中定义关键字，key为关键字，value为TokenType
    private static final Map<String, TokenType> keywords;

    // 初始化keywords
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

    // 用一个列表来保存扫描时产生的token
    public List<Token> scanTokens() {
        // 没有读到文件末尾就一只调用scanToken()函数扫描字符
        while (!isAtEnd()) {
            // 每扫描完一个token，把下一个token的其实位置start变量置为上一个token结束位置+1，即current
            start = current;
            scanToken();
        }
        tokens.add(new Token(EOF, "", null, line));
        return tokens;
    }

    // 消费字符
    private void scanToken() {
        // advance函数获取原文件中当前字符，并将current变量加一(相当于消费掉当前current所指的字符)
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
            // 以下为双字符匹配 读到当前符号后，用match检查下一个符号是否是双字符符号的第二个字符
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
                    // peek不消费字符只会前瞻字符，不会修改current变量，保留句末的\n换行符标志，以便下次进入switch中能匹配到换行符\n对line变量加一
                    while (peek() != '\n' && !isAtEnd()) {
                        advance();
                    }
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
                // 遇到换行符时将line变量加一
                line++;
                break;
            // 处理字符串
            case '"':
                // 匹配到双引号调用string函数
                string();
                break;
            default:
                // 识别数字
                if (isDigit(c)) {
                    number();
                } else if (isAlpha(c)) {
                    // 匹配关键字或变量名
                    // 使用最大匹配的思想，例如关键字or和变量名orchid都可以被匹配到，那么优先选择能匹配到字符数最多的情况，即orchid
                    identifier();
                } else {
                    // 违法的字符
                    Lox.error(line, "Unexpected character.");
                }
                break;
        }
    }

    // 扫描一个标识符(即关键字例如and、or，和变量名)
    private void identifier() {
        // 消费字符和数字
        while (isAlphaNumeric(peek())) {
            advance();
        }
        // 扫描到标识符后，检查是否与map中的某些关键字匹配，如果匹配就使用关键字标识及类型，否则就是一个用户定义的变量名
        String text = source.substring(start, current);
        TokenType type = keywords.get(text);
        if (type == null) {
            type = IDENTIFIER;
        }
        addToken(type);
    }

    // 处理当前扫描到的数字及其后面的数字字符 匹配思路类似string()函数
    private void number() {
        while (isDigit(peek())) {
            advance();
        }
        // 找到小数点并且小数点后面仍有数字
        if (peek() == '.' && isDigit(peekNext())) {
            // 消费小数点.
            advance();
            // 匹配并消费掉小数点后面的数字
            while (isDigit(peek())) {
                advance();
            }
        }
        addToken(NUMBER, Double.parseDouble(source.substring(start, current)));
    }

    // 一直消费字符直到遇到第二个"，如果内容耗尽则报错
    private void string() {
        // 类似判断注释的思路
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
        // 如果想要匹配的下一个字符不是预想的字符，current不移动(相当于不消费下一个字符)，直接返回false
        if (isAtEnd()) {
            return false;
        }
        if (source.charAt(current) != expected) {
            return false;
        }
        // 如果想要匹配的下一个字符是预想的字符，current向后移动一位(相当于消费掉下一个字符)，返回true
        current++;
        return true;
    }

    // 类似advance方法，但不会消费字符(不会移动current)，而是前瞻字符
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

    // 每次判断出一个词后调用addToken函数 获取当前词的文本并为其创建一个新的token（输出）
    private void addToken(TokenType type) {
        addToken(type, null);
    }

    // 同上面的重载方法，处理带有字面值的token
    private void addToken(TokenType type, Object literal) {
        // 变量中具体的内容(例如字符串或数字)
        String text = source.substring(start, current);
        tokens.add(new Token(type, text, literal, line));
    }

    // 是否读到文件的结尾
    private boolean isAtEnd() {
        return current >= source.length();
    }
}
