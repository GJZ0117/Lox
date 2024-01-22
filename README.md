**使用Java实现的一门解释型面向对象编程语言**

**实现了以下语法功能：**
1. 扫描并统计Token
2. 构建抽象语法树AST
3. 计算表达式
4. 变量声明与赋值(var)
5. 语法块、作用域和闭包
6. 分支控制流(if-else)
7. 循环控制流(while、for)
8. 函数的声明与调用(fun)
9. 添加部分内置函数(如clock()用于计时)
10. 类的定义与实例化(class)
11. 构造函数(init())
12. this关键字、类属性和成员方法
13. 继承关系(<)
14. super关键字

**使用方法：**
+ 创建Lox语言源代码文件，与`test01`等测试文件置于同一目录下
+ 在IDEA中编译整个项目(build project)，生成`out/`目录
+ 进入`out/production/lox`目录，使用命令`java cn.gjz.lox.Lox /Users/gjz/Documents/idea-project/lox/src/testFile`解释创建的Lox源代码文件
