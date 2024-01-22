package cn.gjz.lox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static cn.gjz.lox.TokenType.*;

/**
 * 解析表达式
 */

public class Parser {

    private static class ParseError extends RuntimeException {
    }

    private final List<Token> tokens;
    // 指向下一个标记待解析的标记
    private int current = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    // 尽可能多地解析一系列语句，直到命中输入内容的结尾为止 直接将program规则转换为递归下降风格的方式
    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        while (!isAtEnd()) {
            statements.add(declaration());
        }
        return statements;
    }
    // 初始方法来启动解析器
    // Expr parse() {
    //     try {
    //         return expression();
    //     } catch (ParseError error) {
    //         // 当解析器遇到错误，不会崩掉，而是捕捉ParseError返回null
    //         return null;
    //     }
    // }

    // 执行表达式语法，将每一条规则翻译为Java代码
    // expression → equality ;
    // private Expr expression() {
    //     return equality();
    // }
    // expression → assignment ;
    private Expr expression() {
        return assignment();
    }

    private Stmt declaration() {
        try {
            // 匹配class关键字
            if (match(CLASS)) {
                return classDeclaration();
            }
            // 匹配fun关键字 调用function对应的语法规则
            if (match(FUN)) {
                return function("function");
            }
            // 匹配var关键字，判断是否是变量声明语句
            if (match(VAR)) {
                return varDeclaration();
            }
            // 进入statement方法解析print和语句表达式
            return statement();
        } catch (ParseError error) {
            synchronize();
            return null;
        }
    }

    // 解析class定义
    private Stmt classDeclaration() {
        // 查找预期的类名
        Token name = consume(IDENTIFIER, "Expect class name.");
        // 匹配 < 解析可能存在的父类
        Expr.Variable superClass = null;
        if (match(LESS)) {
            consume(IDENTIFIER, "Expect superclass name.");
            superClass = new Expr.Variable(previous());
        }
        // 匹配{
        consume(LEFT_BRACE, "Expect '{' before class body.");
        // 进入类主体 解析方法声明 直到碰到}
        List<Stmt.Function> methods = new ArrayList<>();
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            // 每个方法声明是通过调用function()方法来解析
            methods.add(function("method"));
        }
        consume(RIGHT_BRACE, "Expect '}' after class body.");
        // 如果解析到父类声明，将其保存到AST节点中；如果没有解析到超类子句，超类表达式将是null
        return new Stmt.Class(name, superClass, methods);
    }

    // 解析每一条语句
    private Stmt statement() {
        // 检测当前statement是否以for开头
        if (match(FOR)) {
            return forStatement();
        }
        // 检测当前statement是否以if开头
        if (match(IF)) {
            return ifStatement();
        }
        // 检测当前statement是否以print开头
        if (match(PRINT)) {
            // 调用打印语句
            return printStatement();
        }
        // 匹配return关键字
        if (match(RETURN)) {
            return returnStatement();
        }
        // 检测当前statement是否以while开头
        if (match(WHILE)) {
            return whileStatement();
        }
        // 解析语法块 匹配左大括号{
        if (match(LEFT_BRACE)) {
            return new Stmt.Block(block());
        }
        // 否则调用表达式语句
        return expressionStatement();
    }

    // 解析for表达式语法糖
    private Stmt forStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'for'.");
        Stmt initializer;
        // 如果(后面的标记是分号，那么初始化式就被省略了
        if (match(SEMICOLON)) {
            initializer = null;
        } else if (match(VAR)) {
            // 检查var关键字，看它是否是一个变量声明
            initializer = varDeclaration();
        } else {
            // 如果这两者都不符合，那么它一定是一个表达式，对其进行解析
            initializer = expressionStatement();
        }
        Expr condition = null;
        // 匹配for表达式中第一个分号
        if (!check(SEMICOLON)) {
            // 解析第一个分号后面的条件表达式
            condition = expression();
        }
        // 匹配for表达式中第二个分号
        consume(SEMICOLON, "Expect ';' after loop condition.");
        // 匹配for表达式中第二个分号后面的增量语句
        Expr increment = null;
        if (!check(RIGHT_PAREN)) {
            increment = expression();
        }
        consume(RIGHT_PAREN, "Expect ')' after for clauses.");
        // 循环主体
        Stmt body = statement();
        // 语法糖脱糖：利用上面匹配到的变量合成表示for循环语义的语法树节点，从后向前处理
        if (increment != null) {
            // 如果存在增量子句的话，会在循环的每个迭代中在循环体结束之后执行
            body = new Stmt.Block(Arrays.asList(body, new Stmt.Expression(increment)));
        }
        // 获取条件式和循环体，如果条件式被省略了就使用true来创建一个无限循环
        if (condition == null) {
            condition = new Expr.Literal(true);
        }
        body = new Stmt.While(condition, body);
        if (initializer != null) {
            body = new Stmt.Block(Arrays.asList(initializer, body));
        }
        return body;
    }

    // 解析if表达式
    private Stmt ifStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'if'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after if condition.");
        Stmt thenbranch = statement();
        // 解析else表达式
        Stmt elseBranch = null;
        if (match(ELSE)) {
            elseBranch = statement();
        }
        return new Stmt.If(condition, thenbranch, elseBranch);
    }

    // 解析print后的表达式，消费表示语句终止的分号，并生成语法树
    private Stmt printStatement() {
        Expr value = expression();
        consume(SEMICOLON, "Expect ';' after value.");
        return new Stmt.Print(value);
    }

    private Stmt returnStatement() {
        // 消耗return关键字
        Token keyword = previous();
        Expr value = null;
        // 寻找一个值表达式
        if (!check(SEMICOLON)) {
            value = expression();
        }
        consume(SEMICOLON, "Expect ';' after return value.");
        return new Stmt.Return(keyword, value);
    }

    // 当解析器匹配到一个var标记时，执行如下匹配
    private Stmt varDeclaration() {
        // 消费一个标识符标记作为变量的名称
        Token name = consume(IDENTIFIER, "Expect variable name.");
        Expr initializer = null;
        // 匹配等号
        if (match(EQUAL)) {
            // 匹配初始化表达式
            initializer = expression();
        }
        // 消费语句末尾所需的分号
        consume(SEMICOLON, "Expect ';' after variable declaration.");
        // 封装到一个Stmt.Var语法树节点中
        return new Stmt.Var(name, initializer);
    }

    // 当解析式匹配到while标记时，执行如下匹配
    private Stmt whileStatement() {
        consume(LEFT_PAREN, "Expect '(' after 'while'.");
        Expr condition = expression();
        consume(RIGHT_PAREN, "Expect ')' after condition.");
        Stmt body = statement();
        return new Stmt.While(condition, body);
    }

    // 没有匹配到print语句，那一定是一条下面的语句 解析一个后面带分号的表达式
    private Stmt expressionStatement() {
        Expr expr = expression();
        consume(SEMICOLON, "Expect ';' after expression.");
        return new Stmt.Expression(expr);
    }

    // 解析函数定义语法
    private Stmt.Function function(String kind) {
        // 消费标识符标记作为函数名称
        Token name = consume(IDENTIFIER, "Expect " + kind + " name.");
        consume(LEFT_PAREN, "Expect '(' after " + kind + " name.");
        List<Token> parameters = new ArrayList<>();
        if (!check(RIGHT_PAREN)) {
            do {
                if (parameters.size() >= 255) {
                    error(peek(), "Can't have more than 255 parameters.");
                }
                parameters.add(consume(IDENTIFIER, "Expect parameter name."));
            } while (match(COMMA));
        }
        consume(RIGHT_PAREN, "Expect ')' after parameters.");
        consume(LEFT_BRACE, "Expect '{' before " + kind + " body.");
        List<Stmt> body = block();
        return new Stmt.Function(name, parameters, body);
    }

    // 解析语法块内部
    private List<Stmt> block() {
        // 创建一个空列表将解析语句并将其放入列表中
        List<Stmt> statements = new ArrayList<>();
        // 直至遇到语句块的结尾右大括号}
        while (!check(RIGHT_BRACE) && !isAtEnd()) {
            statements.add(declaration());
        }
        consume(RIGHT_BRACE, "Expect '}' after block.");
        return statements;
    }

    // 赋值操作 将右值表达式节点转换为左值的表示形式
    private Expr assignment() {
        // 查看左边的表达式
        // Expr expr = equality();
        // 将赋值操作的解析代码改为调用or()方法
        Expr expr = or();
        if (match(EQUAL)) {
            Token equals = previous();
            // 递归调用assignment()来解析右侧的值
            Expr value = assignment();
            if (expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable) expr).name;
                return new Expr.Assign(name, value);
            } else if (expr instanceof Expr.Get) {
                // 把左边的表达式作为一个正常表达式来解析
                // 在后面发现等号时，就把已经解析的表达式转换为正确的赋值语法树节点
                Expr.Get get = (Expr.Get) expr;
                // 将左边的Expr.Get表达式转化为相应的Expr.Set表达式
                return new Expr.Set(get.object, get.name, value);
            }
            error(equals, "Invalid assignment target.");
        }
        return expr;
    }

    // 解析一系列or语句
    private Expr or() {
        // or操作数是位于下一优先级的新的and表达式
        Expr expr = and();
        while (match(OR)) {
            Token operator = previous();
            Expr right = and();
            expr = new Expr.Logical(expr, operator, right);
        }
        return expr;
    }

    // 解析and语句
    private Expr and() {
        // 调用 equality() 计算操作数
        Expr expr = equality();
        while (match(AND)) {
            Token operator = previous();
            Expr right = equality();
            expr = new Expr.Logical(expr, operator, right);
        }
        return expr;
    }

    // equality → comparison ( ( "!=" | "==" ) comparison )* ;
    private Expr equality() {
        Expr expr = comparison();
        // 规则中的( ... )*循环映射为一个while循环
        while (match(BANG_EQUAL, EQUAL_EQUAL)) {
            Token operator = previous();
            Expr right = comparison();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    // comparison → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
    private Expr comparison() {
        // 获取操作数时调用的方法是term()而不是comparison()
        Expr expr = term();
        while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
            Token operator = previous();
            Expr right = term();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    // 做加减法
    private Expr term() {
        // 做乘除法
        Expr expr = factor();
        // 做加减法
        while (match(MINUS, PLUS)) {
            Token operator = previous();
            Expr right = factor();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    // 做乘除法
    private Expr factor() {
        Expr expr = unary();
        while (match(SLASH, STAR)) {
            Token operator = previous();
            Expr right = unary();
            expr = new Expr.Binary(expr, operator, right);
        }
        return expr;
    }

    // 匹配函数调用时候的参数列表
    private Expr finalCall(Expr callee) {
        List<Expr> arguments = new ArrayList<>();
        // 判断下一个标记是否)来检查参数列表结束，考虑了有参和无参
        if (!check(RIGHT_PAREN)) {
            do {
                // 限制函数的参数个数
                if (arguments.size() >= 255) {
                    error(peek(), "Can't have more than 255 arguments.");
                }
                // 解析表达式加入参数列表
                arguments.add(expression());
                // 匹配逗号
            } while (match(COMMA));
        }
        // 消费预期的)
        Token paren = consume(RIGHT_PAREN, "Expect ')' after arguments.");
        // 封装成函数调用的AST节点
        return new Expr.Call(callee, paren, arguments);
    }

    // 解析函数调用
    private Expr call() {
        // 解析一个基本表达式
        Expr expr = primary();
        // while循环对应于语法规则中的*
        while (true) {
            // 每次看到(就调用finishCall()解析调用表达式，并使用之前解析出的表达式作为被调用者
            if (match(LEFT_PAREN)) {
                expr = finalCall(expr);
            } else if (match(DOT)) {
                // 查找 . 沿着标记构建一系列的call和get
                Token name = consume(IDENTIFIER, "Expect property name after '.'.");
                expr = new Expr.Get(expr, name);
            } else {
                break;
            }
        }
        return expr;
    }

    // 处理一元运算符
    private Expr unary() {
        if (match(BANG, MINUS)) {
            Token operator = previous();
            Expr right = unary();
            return new Expr.Unary(operator, right);
        }
        return call();
    }

    // 匹配语法 primary->
    private Expr primary() {
        if (match(FALSE)) {
            return new Expr.Literal(false);
        }
        if (match(TRUE)) {
            return new Expr.Literal(true);
        }
        if (match(NIL)) {
            return new Expr.Literal(null);
        }

        if (match(NUMBER, STRING)) {
            return new Expr.Literal(previous().literal);
        }

        // 解析super关键字
        if (match(SUPER)) {
            Token keyword = previous();
            // 消费预期的.和方法名称
            consume(DOT, "Expect '.' after 'super'.");
            Token method = consume(IDENTIFIER, "Expect superclass method name.");
            return new Expr.Super(keyword, method);
        }

        // 解析this关键字
        if (match(THIS)) {
            return new Expr.This(previous());
        }

        // 解析变量表达式
        if (match(IDENTIFIER)) {
            return new Expr.Variable(previous());
        }

        // 如果匹配了一个开头(并解析了里面的表达式后，必须找到一个)标记 如果没有找到就错误
        if (match(LEFT_PAREN)) {
            Expr expr = expression();
            consume(RIGHT_PAREN, "Expect ')' after expression.");
            return new Expr.Grouping(expr);
        }

        throw error(peek(), "Expect expression.");
    }

    // 判断当前的标记是否属于给定的类型之一
    private boolean match(TokenType... types) {
        for (TokenType type : types) {
            if (check(type)) {
                advance();
                return true;
            }
        }
        return false;
    }

    // 调用consume()方法查找收尾的)
    private Token consume(TokenType type, String message) {
        // 检查下一个标记是否是预期的类型，如果是就会消费该标记
        if (check(type)) {
            return advance();
        }
        throw error(peek(), message);
    }

    // 如果当前标记属于给定类型返回true 不消费标记只是读取
    private boolean check(TokenType type) {
        if (isAtEnd()) {
            return false;
        }
        return peek().type == type;
    }

    // 消费当前的标记并返回它
    private Token advance() {
        if (!isAtEnd()) {
            current++;
        }
        return previous();
    }

    // 是否处理完了待解析的标记
    private boolean isAtEnd() {
        return peek().type == EOF;
    }

    // 返回还未消费的当前标记
    private Token peek() {
        return tokens.get(current);
    }

    // 返回上一个消费了的标记
    private Token previous() {
        return tokens.get(current - 1);
    }

    // 报告错误
    private ParseError error(Token token, String message) {
        Lox.error(token, message);
        return new ParseError();
    }

    // 丢弃标记直至达到下一条语句的开头(不断丢弃标记，直到发现一个语句的边界)
    private void synchronize() {
        advance();
        while (!isAtEnd()) {
            // 遇到分号前进到下一个statement开头
            if (previous().type == SEMICOLON) {
                return;
            }
            // 通过以下关键词判断是否是新的statement开头
            switch (peek().type) {
                case CLASS:
                case FUN:
                case VAR:
                case FOR:
                case IF:
                case WHILE:
                case PRINT:
                case RETURN:
                    return;
            }
            // 没有到下一个statement的开头就前进
            advance();
        }
    }
}
