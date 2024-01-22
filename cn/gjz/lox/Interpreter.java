package cn.gjz.lox;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 解释器计算算数表达式的值
 */

public class Interpreter implements Expr.Visitor<Object>, Stmt.Visitor<Void> {

    // 将变量留在内存中
    // private Environment environment = new Environment();
    // globals字段固定指向最外层的全局作用域
    final Environment globals = new Environment();
    // environment字段会随着进入和退出局部作用域而改变，会跟随当前环境
    private Environment environment = globals;
    // 把解析信息存储在map中，将每个语法树节点与其解析的数据关联起来
    private final Map<Expr, Integer> locals = new HashMap<>();

    // 实例化一个解释器时，将全局作用域中添加本地函数
    Interpreter() {
        // 一个名为clock的变量，它的值是一个实现LoxCallable接口的Java匿名类
        // 一个本地函数，用于返回自某个固定时间点以来所经过的秒数，两次连续调用之间的差值可计算出两次调用之间经过了多少时间
        // clock()函数不接受参数，其元数为0
        // call()方法的实现是直接调用Java函数并将结果转换为以秒为单位的double值
        globals.define("clock", new LoxCallable() {
            @Override
            public int arity() {
                return 0;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                return (double) System.currentTimeMillis() / 1000.0;
            }

            @Override
            public String toString() {
                return "<native fn>";
            }
        });
    }

    // 解释器对外暴露的API(接受一组语句即一段程序)
    // public void interpret(Expr expresion) {
    //     try {
    //         Object value = evaluate(expresion);
    //         System.out.println(stringify(value));
    //     } catch (RuntimeError error) {
    //         Lox.runtimeError(error);
    //     }
    // }
    // 对于每一个statement都需要进行解释(计算)
    public void interpret(List<Stmt> statements) {
        try {
            for (Stmt statement : statements) {
                execute(statement);
            }
        } catch (RuntimeError error) {
            Lox.runtimeError(error);
        }
    }

    // 二元操作符求值
    @Override
    public Object visitBinaryExpr(Expr.Binary expr) {
        // 后缀表达式 先计算左右子节点 再找父节点(当前节点)操作符
        Object left = evaluate(expr.left);
        Object right = evaluate(expr.right);
        switch (expr.operator.type) {
            case PLUS:
                // 数字加法
                if (left instanceof Double && right instanceof Double) {
                    return (double) left + (double) right;
                }
                // 字符串拼接
                if (left instanceof String && right instanceof String) {
                    return (String) left + (String) right;
                }
                throw new RuntimeError(expr.operator, "Operands must be two numbers or two strings.");
            case MINUS:
                checkNumberOperands(expr.operator, left, right);
                return (double) left - (double) right;
            case SLASH:
                checkNumberOperands(expr.operator, left, right);
                return (double) left / (double) right;
            case STAR:
                checkNumberOperands(expr.operator, left, right);
                return (double) left * (double) right;
            case GREATER:
                checkNumberOperands(expr.operator, left, right);
                return (double) left > (double) right;
            case GREATER_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double) left >= (double) right;
            case LESS:
                checkNumberOperands(expr.operator, left, right);
                return (double) left < (double) right;
            case LESS_EQUAL:
                checkNumberOperands(expr.operator, left, right);
                return (double) left <= (double) right;
            case BANG_EQUAL:
                return !isEqual(left, right);
            case EQUAL_EQUAL:
                return isEqual(left, right);
        }
        return null;
    }

    @Override
    public Object visitCallExpr(Expr.Call expr) {
        // 对被调用者的表达式求值
        Object callee = evaluate(expr.callee);
        // 依次对每个参数表达式求值 将结果值存储在一个列表中
        List<Object> arguments = new ArrayList<>();
        for (Expr argument : expr.arguments) {
            arguments.add(evaluate(argument));
        }
        // 检查掉能否背调用
        if (!(callee instanceof LoxCallable)) {
            throw new RuntimeError(expr.paren, "Can only call functions and classes.");
        }
        // 将被调用者转换为LoxCallable，对其调用call()方法来实现
        LoxCallable function = (LoxCallable) callee;
        // 检查参数列表的长度是否与可调用方法的元数相符
        if (arguments.size() != function.arity()) {
            throw new RuntimeError(expr.paren, "Expected " + function.arity() + " arguments but got " + arguments.size() + ".");
        }
        return function.call(this, arguments);
    }

    @Override
    public Object visitGetExpr(Expr.Get expr) {
        // 对属性被访问的表达式求值(只有类的实例才具有属性)
        Object object = evaluate(expr.object);
        // 如果该对象是LoxInstance就要求它去查找该属性
        if (object instanceof LoxInstance) {
            return ((LoxInstance) object).get(expr.name);
        }
        throw new RuntimeError(expr.name, "Only instances have properties.");
    }

    // 括号中内容求值 在表达式中显式使用括号时产生的语法树节点
    @Override
    public Object visitGroupingExpr(Expr.Grouping expr) {
        return evaluate(expr.expression);
    }

    // 将表达式发送回解释器的访问者实现中
    private Object evaluate(Expr expr) {
        return expr.accept(this);
    }

    // 处理语句,类似于处理表达式的evaluate()方法
    private void execute(Stmt stmt) {
        stmt.accept(this);
    }

    // 把解析信息存储在map中，将每个语法树节点与其解析的数据关联起来
    void resolve(Expr expr, int depth) {
        locals.put(expr, depth);
    }

    // 在给定的环境上下文中执行一系列语句
    public void executeBlock(List<Stmt> statemetns, Environment environment) {
        // 保存全局环境
        Environment previous = this.environment;
        try {
            // 向当前环境(要执行的代码的最内层作用域相对应的环境)
            this.environment = environment;
            // 执行所有的语句
            for (Stmt statement : statemetns) {
                execute(statement);
            }
        } finally {
            // 恢复之前的环境
            this.environment = previous;
        }
    }

    // 语法块语义
    @Override
    public Void visitBlockStmt(Stmt.Block stmt) {
        // 要执行一个语法块，要先为该块作用域创建一个新的环境
        executeBlock(stmt.statements, new Environment(environment));
        return null;
    }

    // 解释类的声明
    @Override
    public Void visitClassStmt(Stmt.Class stmt) {
        Object superClass = null;
        // 如果类中有父类表达式就对其求值
        if (stmt.superclass != null) {
            superClass = evaluate(stmt.superclass);
            // 运行时必须检查希望作为父类的对象是否确实是一个类
            if (!(superClass instanceof LoxClass)) {
                throw new RuntimeError(stmt.superclass.name, "Superclass must be a class.");
            }
        }
        // 在当前环境中声明该类的名称
        environment.define(stmt.name.lexeme, null);
        // 当执行子类定义时，创建一个新环境
        if (stmt.superclass != null) {
            // 保存指向父类的引用
            environment = new Environment(environment);
            environment.define("super", superClass);
        }
        // 把类的语法节点转换为LoxClass 即类的运行时表示
        Map<String, LoxFunction> methods = new HashMap<>();
        // 把类的语法表示（其AST节点）变成它的运行时表示，对类中包含的方法进行这样的操作
        // 每个方法声明都会变成一个LoxFunction对象
        // 把所有这些都打包到一个map中，以方法名称作为键。这些数据存储在LoxClass中
        for (Stmt.Function method : stmt.methods) {
            // 对于方法来说检查其名称是否为init构造函数
            LoxFunction function = new LoxFunction(method, environment, method.name.lexeme.equals("init"));
            methods.put(method.name.lexeme, function);
        }
        LoxClass klass = new LoxClass(stmt.name.lexeme, (LoxClass) superClass, methods);
        // 弹出父类环境
        if (superClass != null) {
            environment = environment.enclosing;
        }
        // 将类对象存储在之前声明的变量中
        environment.assign(stmt.name, klass);
        return null;
    }

    // 语句不会产生值，因此visit方法的返回类型是Void
    @Override
    public Void visitExpressionStmt(Stmt.Expression stmt) {
        evaluate(stmt.expression);
        return null;
    }

    // 封装了语法节点的LoxFunction实例
    @Override
    public Void visitFunctionStmt(Stmt.Function stmt) {
        // 对于实际的函数声明， isInitializer取值总是false
        LoxFunction function = new LoxFunction(stmt, environment, false);
        environment.define(stmt.name.lexeme, function);
        return null;
    }

    // 解释if语句
    @Override
    public Void visitIfStmt(Stmt.If stmt) {
        // 对if表达式进行求值
        if (isTruthy(evaluate(stmt.condition))) {
            // 执行if的then分支
            execute(stmt.thenBranch);
        } else if (stmt.elseBranch != null) {
            // 执行else的then分支
            execute(stmt.elseBranch);
        }
        return null;
    }

    // 用stringify方法转换为字符串，然后输出到stdout
    @Override
    public Void visitPrintStmt(Stmt.Print stmt) {
        // 计算中间的expression结果
        Object value = evaluate(stmt.expression);
        System.out.println(stringify(value));
        return null;
    }

    // 如果有返回值，就对其求值，否则就使用nil
    // 取这个值并将其封装在一个自定义的异常类中，并抛出该异常
    @Override
    public Void visitReturnStmt(Stmt.Return stmt) {
        Object value = null;
        if (stmt.value != null) {
            value = evaluate(stmt.value);
        }
        throw new Return(value);
    }

    // 对有初始化公式的变量进行求值并保存到environment中
    @Override
    public Void visitVarStmt(Stmt.Var stmt) {
        Object value = null;
        // 如果该变量有初始化式就对其求值
        if (stmt.initializer != null) {
            value = evaluate(stmt.initializer);
        }
        environment.define(stmt.name.lexeme, value);
        return null;
    }

    // 解析while表达式
    @Override
    public Void visitWhileStmt(Stmt.While stmt) {
        while (isTruthy(evaluate(stmt.condition))) {
            execute(stmt.body);
        }
        return null;
    }

    // 对右侧表达式运算以获取值，然后将其保存到命名变量中
    @Override
    public Object visitAssignExpr(Expr.Assign expr) {
        Object value = evaluate(expr.value);
        // environment.assign(expr.name, value);
        // 查找变量的作用域距离
        Integer distance = locals.get(expr);
        if (distance != null) {
            environment.assignAt(distance, expr.name, value);
        } else {
            // 没有找到假定它是全局变量
            globals.assign(expr.name, value);
        }
        return value;
    }

    // 字面量求值 将字面量树节点转换为运行时值
    @Override
    public Object visitLiteralExpr(Expr.Literal expr) {
        return expr.value;
    }

    // 解析逻辑表达式
    @Override
    public Object visitLogicalExpr(Expr.Logical expr) {
        // 计算左操作数
        Object left = evaluate(expr.left);
        // 判断是否可以短路
        if (expr.operator.type == TokenType.OR) {
            if (isTruthy(left)) {
                return left;
            }
        } else {
            if (!isTruthy(left)) {
                return left;
            }
        }
        // 当且仅当不能短路时才计算右侧的操作数
        return evaluate(expr.right);
    }

    @Override
    public Object visitSetExpr(Expr.Set expr) {
        // 计算出被设置属性的对象
        Object object = evaluate(expr.object);
        // 检查它是否是一个LoxInstance
        if (!(object instanceof LoxInstance)) {
            throw new RuntimeError(expr.name, "Only instances have fields.");
        }
        // 计算设置的值，并将其保存到该实例中
        Object value = evaluate(expr.value);
        ((LoxInstance) object).set(expr.name, value);
        return value;
    }

    @Override
    public Object visitSuperExpr(Expr.Super expr) {
        // 在适当环境中查找“super”来找到外围类的父类
        int distance = locals.get(expr);
        LoxClass superClass = (LoxClass) environment.getAt(distance, "super");
        // 将距离偏移1，在那个内部环境中查找“this”
        LoxInstance object = (LoxInstance) environment.getAt(distance - 1, "this");
        // 从父类开始查找并绑定方法(在超类上调用findMethod() 而不是在当前对象的类)
        LoxFunction method = superClass.findMethod(expr.method.lexeme);
        if (method == null) {
            throw new RuntimeError(expr.method, "Undefined property '" + expr.method.lexeme + "'.");
        }
        return method.bind(object);
    }

    // 解析this
    @Override
    public Object visitThisExpr(Expr.This expr) {
        return lookUpVariable(expr.keyword, expr);
    }

    // 一元表达式求值 一元表达式自身在完成求值之后还会做一些工作
    @Override
    public Object visitUnaryExpr(Expr.Unary expr) {
        Object right = evaluate(expr.right);
        switch (expr.operator.type) {
            case BANG:
                return !isTruthy(right);
            case MINUS:
                checkNumberOperand(expr.operator, right);
                return -(double) right;
        }
        return null;
    }

    // 将操作转发到环境上下文中
    @Override
    public Object visitVariableExpr(Expr.Variable expr) {
        // return environment.get(expr.name);
        return lookUpVariable(expr.name, expr);
    }

    private Object lookUpVariable(Token name, Expr expr) {
        // 在map中查找已解析的距离值(只解析了本地变量，全局变量被特殊处理了，不会出现了map中)
        Integer distance = locals.get(expr);
        if (distance != null) {
            return environment.getAt(distance, name.lexeme);
        } else {
            // 如果没有在map中找到变量对应的距离值，它一定是全局变量
            return globals.get(name);
        }
    }

    // 对传入的参数进行Boolean值的判断
    private boolean isTruthy(Object object) {
        if (object == null) {
            return false;
        }
        if (object instanceof Boolean) {
            return (boolean) object;
        }
        return true;
    }

    // 相等判断
    private boolean isEqual(Object a, Object b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null) {
            return false;
        }
        return a.equals(b);
    }

    // 检查一元表达式的对象类型是否为操作数
    private void checkNumberOperand(Token operator, Object operand) {
        if (operand instanceof Double) {
            return;
        }
        throw new RuntimeError(operator, "Operand must be a number.");
    }

    // 检查二元表达式的对象类型是否为操作数
    private void checkNumberOperands(Token operator, Object left, Object right) {
        if (left instanceof Double && right instanceof Double) {
            return;
        }
        throw new RuntimeError(operator, "Operands must be numbers.");
    }

    // 连接了Lox对象的用户视图和它们在Java中的内部表示
    private String stringify(Object object) {
        if (object == null) {
            return "nil";
        }
        if (object instanceof Double) {
            String text = object.toString();
            if (text.endsWith(".0")) {
                text = text.substring(0, text.length() - 2);
            }
            return text;
        }
        return object.toString();
    }
}
