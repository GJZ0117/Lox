package cn.gjz.lox;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

/**
 * 将变量解析处理也放在一个类中
 */

public class Resolver implements Expr.Visitor<Void>, Stmt.Visitor<Void> {

    private final Interpreter interpreter;

    // 记录当前作用域内的栈 栈中的每个元素是代表一个块作用域的Map，key是变量名，value代表是否已经结束了对变量初始化式的解析
    private final Stack<Map<String, Boolean>> scopes = new Stack<>();

    private FunctionType currentFunction = FunctionType.NONE;

    private enum FunctionType {
        NONE,
        FUNCTION,
        INITIALIZER,
        METHOD
    }

    // 告诉解析器在遍历语法树时，目前是否在一个类声明中
    private enum ClassType {
        NONE,
        CLASS,
        SUBCLASS
    }

    // 一开始是NONE意味着不在类中
    private ClassType currentClass = ClassType.NONE;

    Resolver(Interpreter interpreter) {
        this.interpreter = interpreter;
    }

    // 块语法（块语法创建了局部作用域）
    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        beginScope();
        resolve(stmt.statements);
        endScope();
        return null;
    }

    // 将名称和方法列表封装到Stmt.Class节点中 进入分析器中对节点进行分析
    // 只要遇到this表达式（至少是在方法内部），它就会解析为一个“局部变量”，该变量定义在方法体块之外的隐含作用域中
    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        // 与currentFunction一样，将字段的前一个值存储在一个局部变量中，在JVM中保持一个currentClass的栈
        // 如果一个类嵌套在另一个类中，就不会丢失对前一个值的跟踪
        ClassType enclosingClass = currentClass;
        currentClass = ClassType.CLASS;
        declare(stmt.name);
        define(stmt.name);
        // 避免类继承自己的情况
        if (stmt.superclass != null && stmt.name.lexeme.equals(stmt.superclass.name.lexeme)) {
            Lox.error(stmt.superclass.name, "A class can't inherit from itself.");
        }
        // 遍历并分析类声明的AST节点中新的子表达式
        if (stmt.superclass != null) {
            currentClass = ClassType.SUBCLASS;
            resolve(stmt.superclass);
        }
        // 如果该类声明有父类，那么就在其所有方法的外围创建一个新的作用域 super
        if (stmt.superclass != null) {
            beginScope();
            scopes.peek().put("super", true);
        }
        // 开始分析方法体之前，推入一个新的作用域，并在其中像定义变量一样定义“this”
        beginScope();
        scopes.peek().put("this", true);
        // 遍历类主体中的方法
        for (Stmt.Function method : stmt.methods) {
            FunctionType declaration = FunctionType.METHOD;
            // 通过被访问方法的名称来确定是否在分析一个构造方法
            if (method.name.lexeme.equals("init")) {
                declaration = FunctionType.INITIALIZER;
            }
            // 调用已经写好的用来处理函数声明的resolveFunction()方法
            resolveFunction(method, declaration);
        }
        // 完成后会丢弃这个外围作用域
        endScope();
        // 完成了对父类中方法的分析就丢弃这个作用域
        if (stmt.superclass != null) {
            endScope();
        }
        // 通过恢复旧值来“弹出”堆栈
        currentClass = enclosingClass;
        return null;
    }

    // 一个表达式语句中包含一个需要遍历的表达式
    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        resolve(stmt.expression);
        return null;
    }

    // 解析函数声明
    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        declare(stmt.name);
        define(stmt.name);
        // 解析待赋值的变量
        resolveFunction(stmt, FunctionType.FUNCTION);
        return null;
    }

    // if语句包含一个条件表达式，以及一个或两个分支语句
    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        resolve(stmt.condition);
        resolve(stmt.thenBranch);
        if (stmt.elseBranch != null) {
            resolve(stmt.elseBranch);
        }
        return null;
    }

    // print语句包含一个子表达式
    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        resolve(stmt.expression);
        return null;
    }

    // return语句包含一个子表达式
    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        // 在解析return语句时进行检查是否在一个函数体内
        if (currentFunction == FunctionType.NONE) {
            Lox.error(stmt.keyword, "Can't return from top-level code.");
        }
        if (stmt.value != null) {
            // 静态地禁止了构造方法返回一个值
            if (currentFunction == FunctionType.INITIALIZER) {
                Lox.error(stmt.keyword, "Can't return a value from an initializer.");
            }
            resolve(stmt.value);
        }
        return null;
    }

    // 解析一个变量声明
    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        declare(stmt.name);
        // 初始化表达式完成，变量也就绪，将map中变量对应的value改为true
        if (stmt.initializer != null) {
            resolve(stmt.initializer);
        }
        define(stmt.name);
        return null;
    }

    // while语句 解析条件 解析一次循环体
    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        resolve(stmt.condition);
        resolve(stmt.body);
        return null;
    }

    // 解析赋值表达式
    @Override
    public Void visitAssignExpr(Expr.Assign expr) {
        resolve(expr.value);
        resolveLocal(expr, expr.name);
        return null;
    }

    // 二元表达式 遍历并解析两个操作数
    @Override
    public Void visitBinaryExpr(Expr.Binary expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    // 函数调用 遍历参数列表并解析
    @Override
    public Void visitCallExpr(Expr.Call expr) {
        resolve(expr.callee);
        for (Expr argument : expr.arguments) {
            resolve(argument);
        }
        return null;
    }

    // 动态查找类的属性
    @Override
    public Void visitGetExpr(Expr.Get expr) {
        resolve(expr.object);
        return null;
    }

    // 括号表达式
    @Override
    public Void visitGroupingExpr(Expr.Grouping expr) {
        resolve(expr.expression);
        return null;
    }

    // 字面量表达式
    @Override
    public Void visitLiteralExpr(Expr.Literal expr) {
        return null;
    }

    // 逻辑表达式 与二元运算符一样
    @Override
    public Void visitLogicalExpr(Expr.Logical expr) {
        resolve(expr.left);
        resolve(expr.right);
        return null;
    }

    // 解析setter方法 递归到Expr.Set的两个子表达式中，即被设置属性的对象和它被设置的值
    @Override
    public Void visitSetExpr(Expr.Set expr) {
        resolve(expr.value);
        resolve(expr.object);
        return null;
    }

    // 解析super表达式
    @Override
    public Void visitSuperExpr(Expr.Super expr) {
        // 解析super表达式时，会检查当前是否在一个允许使用super表达式的作用域中
        if (currentClass == ClassType.NONE) {
            Lox.error(expr.keyword, "Can't use 'super' outside of a class.");
        } else if (currentClass != ClassType.SUBCLASS) {
            Lox.error(expr.keyword, "Can't use 'super' in a class with no superclass.");
        }
        // 把super标记当作一个变量进行分析，分析结果保存了解释器要在环境链上找到超类所在的环境需要的跳数
        resolveLocal(expr, expr.keyword);
        return null;
    }

    // 解析this 使用this作为“变量”的名称，并像其它局部变量一样对其分析
    @Override
    public Void visitThisExpr(Expr.This expr) {
        // 如果this表达式没有出现在一个方法体内，currentClass提供了报告错误所需的数据
        if (currentClass == ClassType.NONE) {
            Lox.error(expr.keyword, "Can't use 'this' outside of a class.");
            return null;
        }
        resolveLocal(expr, expr.keyword);
        return null;
    }

    // 最后一个节点 解析它的一个操作数
    @Override
    public Void visitUnaryExpr(Expr.Unary expr) {
        resolve(expr.right);
        return null;
    }

    // 解析变量表达式
    @Override
    public Void visitVariableExpr(Expr.Variable expr) {
        // 检查变量是否在其自身的初始化式中被访问
        if (!scopes.isEmpty() && scopes.peek().get(expr.name.lexeme) == Boolean.FALSE) {
            // 如果当前作用域中存在该变量，但是它的值是false，意味着已经声明了它，但是还没有定义它。我们会报告一个错误出来
            Lox.error(expr.name, "Can't read local variable in its own initializer.");
        }
        resolveLocal(expr, expr.name);
        return null;
    }

    // 遍历语句列表，并解析其中每一条语句
    private void resolve(Stmt stmt) {
        stmt.accept(this);
    }

    // 解析表达式时会用到的重载方法
    private void resolve(Expr expr) {
        expr.accept(this);
    }

    // 开始一个新的作用域，遍历块中的语句，然后丢弃该作用域
    void resolve(List<Stmt> statements) {
        for (Stmt statement : statements) {
            resolve(statement);
        }
    }

    // 解析函数体
    private void resolveFunction(Stmt.Function function, FunctionType type) {
        // 在解析函数体之前将FunctionType保存在字段中
        FunctionType enclosingFunction = currentFunction;
        currentFunction = type;
        // 为函数体创建一个新的作用域
        beginScope();
        // 为函数的每个参数绑定变量
        for (Token param : function.params) {
            declare(param);
            define(param);
        }
        // 在这个作用域中解析函数体
        resolve(function.body);
        endScope();
        // 完成函数体的解析之后将该字段恢复为之前的值
        currentFunction = enclosingFunction;
    }

    // 创建一个新的块作用域
    private void beginScope() {
        // 解释器是使用链表（Environment对象组成的链）来实现栈的，在解析器中，使用一个真正的Java Stack
        scopes.push(new HashMap<String, Boolean>());
    }

    // 退出作用域
    private void endScope() {
        // 作用域被存储在一个显式的栈中 直接退栈
        scopes.pop();
    }

    // 声明将变量添加到最内层的作用域，这样它就会遮蔽任何外层作用域
    private void declare(Token name) {
        if (scopes.isEmpty()) {
            return;
        }
        Map<String, Boolean> scope = scopes.peek();
        // 在局部作用域中声明一个变量时，已经知道了之前在同一作用域中声明的每个变量的名字
        // 如果看到有冲突，就报告一个错误
        if (scope.containsKey(name.lexeme)) {
            Lox.error(name, "Already variable with this name in this scope.");
        }
        // 过在作用域map中将其名称绑定到false来表明该变量“尚未就绪”
        scope.put(name.lexeme, false);
    }

    // 在作用域map中将变量的值置为true，以标记它已完全初始化并可使用
    private void define(Token name) {
        if (scopes.isEmpty()) {
            return;
        }
        scopes.peek().put(name.lexeme, true);
    }

    // 解析变量
    private void resolveLocal(Expr expr, Token name) {
        // 从最内层的作用域开始，向外扩展，在每个map中寻找一个可以匹配的名称
        for (int i = scopes.size() - 1; i >= 0; i--) {
            // 找到了这个变量就对其解析
            if (scopes.get(i).containsKey(name.lexeme)) {
                interpreter.resolve(expr, scopes.size() - 1 - i);
                return;
            }
        }
        // 如果遍历了所有的作用域也没有找到这个变量就不解析它，并假定它是一个全局变量
    }
}
