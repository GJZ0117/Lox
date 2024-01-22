package cn.gjz.tool;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

/**
 * 表示代码
 * 微型Java命令行应用程序，生成一个名为 Expr.java 的文件
 * 需要将命令行当前位置切换到src目录下才能编译运行
 * 编译 javac cn/gjz/tool/GenerateAst.java
 * 运行 java cn.gjz.tool.GenerateAst /Users/gjz/Documents/idea-project/lox/src/cn/gjz/lox/
 */

public class GenerateAst {
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: generate_ast <output directory>");
            System.exit(64);
        }
        String outputDir = args[0];
        // 对每种类型及字段进行一些描述
        defineAst(outputDir, "Expr", Arrays.asList(
                "Assign   : Token name, Expr value",
                "Binary   : Expr left, Token operator, Expr right",
                "Call     : Expr callee, Token paren, List<Expr> arguments",
                "Get      : Expr object, Token name",
                "Grouping : Expr expression",
                "Literal  : Object value",
                "Logical  : Expr left, Token operator, Expr right",
                "Set      : Expr object, Token name, Expr value",
                "Super    : Token keyword, Token method",
                "This     : Token keyword",
                "Unary    : Token operator, Expr right",
                "Variable : Token name"
        ));

        // 定义Stmt和它的子类
        defineAst(outputDir, "Stmt", Arrays.asList(
                "Block      : List<Stmt> statements",
                "Class      : Token name, Expr.Variable superclass, List<Stmt.Function> methods",
                "Expression : Expr expression",
                "Function   : Token name, List<Token> params, List<Stmt> body",
                "If         : Expr condition, Stmt thenBranch, Stmt elseBranch",
                "Print      : Expr expression",
                "Return     : Token keyword, Expr value",
                "Var        : Token name, Expr initializer",
                "While      : Expr condition, Stmt body"
        ));
    }

    // 输出基类Expr
    private static void defineAst(String outputDir, String baseName, List<String> types) throws IOException {
        String path = outputDir + "/" + baseName + ".java";
        PrintWriter writer = new PrintWriter(path, "UTF-8");

        writer.println("package cn.gjz.lox;");
        writer.println();
        writer.println("import java.util.List;");
        writer.println();
        writer.println("abstract class " + baseName + " {");
        // 定义表达式访问者
        defineVisitor(writer, baseName, types);
        // 访问者模式中定义抽象accept方法
        writer.println("    abstract <R> R accept(Visitor<R> visitor);");
        writer.println();

        // 生成4个内联类
        for (String type : types) {
            String className = type.split(":")[0].trim();
            String fields = type.split(":")[1].trim();
            defineType(writer, baseName, className, fields);
        }
        writer.println("}");
        writer.close();
    }

    // 访问者模式中生成visitor接口
    private static void defineVisitor(PrintWriter writer, String baseName, List<String> types) {
        writer.println("    interface Visitor<R> {");
        for (String type : types) {
            String typeName = type.split(":")[0].trim();
            writer.println("        R visit" + typeName + baseName + "(" + typeName + " " + baseName.toLowerCase() + ");");
            writer.println();
        }
        writer.println("    }");
        writer.println();
    }

    private static void defineType(PrintWriter writer, String baseName, String className, String fieldList) {
        // 类名
        writer.println("    static class " + className + " extends " + baseName + " {");
        // 传入构造器的参数
        String[] fields = fieldList.split(", ");
        // 子类中的成员变量
        writer.println();
        for (String field : fields) {
            writer.println("        final " + field + ";");
        }
        writer.println();
        // 构造器
        writer.println("        " + className + "(" + fieldList + ") {");
        // 在构造器中将传入的参数赋值给类成员变量
        for (String field : fields) {
            String name = field.split(" ")[1];
            writer.println("            this." + name + " = " + name + ";");
        }
        writer.println("        }");
        // 子类中实现accept方法并调用其类型对应的visit方法
        writer.println();
        writer.println("        @Override");
        writer.println("        <R> R accept(Visitor<R> visitor) {");
        writer.println("            return visitor.visit" + className + baseName + "(this);");
        writer.println("        }");
        writer.println("    }");
        writer.println();
    }
}
