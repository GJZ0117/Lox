package cn.gjz.lox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;


/**
 * 进入 out/production/lox 目录下
 * 执行 java cn.gjz.lox.Lox /Users/gjz/Documents/idea-project/lox/src/test 测试
 */

public class Lox {

    // 解释器
    private static final Interpreter interpreter = new Interpreter();

    // 确保解释器不会尝试执行有错误的代码
    static boolean hadError = false;
    static boolean hadRuntimeError = false;

    public static void main(String[] args) throws IOException {
        // 参数个数大于1报错
        if (args.length > 1) {
            System.out.println("Usage: jlox [script]");
            System.exit(64);
        } else if (args.length == 1) {
            // 参数个数等于1，解析参数中提供的源代码文件
            runFile(args[0]);
        } else {
            // 参数个数为0，命令行交互的方式启动
            runPrompt();
        }
    }

    // 从命令行启动jlox并为其提供文件路径，读取文件并执行
    private static void runFile(String path) throws IOException {
        // 读取源代码文件
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        // 将源代码文件送到run这个函数中进行处理
        run(new String(bytes, Charset.defaultCharset()));

        // 如果代码中出现错误则停止运行并退出
        if (hadError) {
            System.exit(65);
        }
        if (hadRuntimeError) {
            System.exit(70);
        }
    }

    // 交互式的启动解释器，通过命令行与解释器进行逐句对话，启动时不需要加任何参数
    private static void runPrompt() throws IOException {
        InputStreamReader input = new InputStreamReader(System.in);
        BufferedReader reader = new BufferedReader(input);

        while (true) {
            System.out.print("> ");
            String line = reader.readLine();
            // 输入ctrl+d发出结束信号
            if (line == null) {
                break;
            }
            // 将命令行中输入的一行代码送到run函数中进行解析
            run(line);
            // 如果用户输入有误，不应该终止整个会话
            hadError = false;
        }
    }

    // 交互式提示符和文件运行工具都通过这个核心函数运行
    private static void run(String source) {
        Scanner scanner = new Scanner(source);
        // 读出所有token
        List<Token> tokens = scanner.scanTokens();

        // 输出所有扫描到的token
        System.out.println("Scanning - Tokens:");
        for (Token token : tokens) {
            System.out.println(token);
        }

        System.out.println("----------");

        Parser parser = new Parser(tokens);
        // Expr expression = parser.parse();
        List<Stmt> statements = parser.parse();
        if (hadError) {
            return;
        }

        // System.out.println("Parsing - Ast printer output:");
        // System.out.println(new AstPrinter().print(expression));
        // System.out.println("----------");
        // System.out.println("Execution - Expression interpreter output:");

        Resolver resolver = new Resolver(interpreter);
        if (hadError) {
            return;
        }
        resolver.resolve(statements);

        // interpreter.interpret(expression);
        interpreter.interpret(statements);
    }

    // 错误处理 传入错误发生的行数和错误信息
    static void error(int line, String message) {
        report(line, " ", message);
    }

    // 打印出错误提示 将hadError置为true
    private static void report(int line, String where, String message) {
        System.err.println("[line " + line + "] Error" + where + ": " + message);
        hadError = true;
    }

    // 向用户展示错误信息
    public static void error(Token token, String message) {
        if (token.type == TokenType.EOF) {
            report(token.line, " at end", message);
        } else {
            report(token.line, " at '" + token.lexeme + "'", message);
        }
    }

    // 计算表达式时出现了运行时错误
    public static void runtimeError(RuntimeError error) {
        System.err.println(error.getMessage() + "\n[line " + error.token.line + "]");
        hadError = true;
    }
}
